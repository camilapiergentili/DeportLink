# Class Management MVP Design

> Segunda etapa de diseño. Toma como base
> [docs/class-management-design-analysis.md](class-management-design-analysis.md) y cierra las
> decisiones necesarias para implementar un MVP concreto. Documento de diseño puro — no se creó
> ni modificó ningún archivo de código para producirlo. Todas las referencias a clases y archivos
> corresponden al estado real del repositorio verificado en esta sesión (incluyendo
> `application.properties`, `application-dev.properties`, migraciones Flyway existentes,
> `UserDetailsServiceImpl` y los adapters de `Reservation`).

---

## 1. Objetivo

Definir, a nivel de diseño (dominio, casos de uso, seguridad, persistencia y API), la versión más
pequeña del módulo de gestión de clases que le permita a un instructor de la academia reemplazar
la coordinación manual por WhatsApp por un panel dentro de DeportLink, sin construir nada que el
flujo real descripto no necesite todavía.

## 2. Alcance del MVP

Incluye:

- Rol `INSTRUCTOR` con login propio (JWT, igual que `OWNER`/`PLAYER`).
- Un instructor puede definir sus clases recurrentes (`ClassSlot`): cancha, día de semana, hora,
  duración, nivel y capacidad.
- Un instructor puede armar el grupo fijo de alumnos de un `ClassSlot` (`ClassEnrollment`),
  agregando y quitando `Player`s existentes.
- Un instructor puede pausar/reactivar un `ClassSlot` (deja de generar clases nuevas, sin borrar
  el historial ni el grupo).
- Un instructor puede crear manualmente la ocurrencia concreta de una clase en una fecha
  (`ClassSession`) a partir de un `ClassSlot`.
- Al crear una `ClassSession`, se genera automáticamente una fila de asistencia
  (`ClassAttendance`, estado inicial `PENDING`) por cada alumno actualmente activo en el
  `ClassEnrollment` de ese `ClassSlot`.
- Un instructor ve el detalle de una `ClassSession`: cancha, nivel, capacidad, ocupación actual
  (`3/4`) y el estado de cada alumno.
- Un instructor confirma o cancela manualmente la asistencia de un alumno a una `ClassSession`
  (acción real, y la misma acción sirve para la "simulación de WhatsApp" — ver sección 16).
- `ADMIN` tiene acceso global de solo-gestión sobre todo lo anterior (mismo patrón que ya existe
  para Club/Branch/Court).
- Validación de conflicto de cancha entre `Reservation` y `ClassSession` (sección 9).
- UI mobile-first (fuera del alcance de este documento de backend, pero condiciona la forma de la
  API: respuestas ya "armadas" para pantalla, ver sección 13).

No incluye (ver sección 3).

## 3. Fuera de alcance

Explícitamente diferido, según instrucción del usuario:

- WhatsApp Business API real y sus webhooks.
- `@Scheduled` / jobs / generación automática de `ClassSession`s desde `ClassSlot` (Etapa 3 del
  documento anterior).
- Lista de espera (`waitlist`).
- Matchmaking / "Tinder de pádel".
- Estadísticas y dashboards avanzados.
- Notificaciones reales (push, email, SMS).
- Integraciones con proveedores externos.
- Pagos.
- Autoservicio de `Player` para inscribirse/reservar una clase nueva por su cuenta (el modelo lo
  permite a futuro — sección 10 — pero ningún endpoint de este MVP lo expone).
- Cancelación de una `ClassSession` completa (el campo de estado existe en el modelo porque el
  usuario lo pidió explícitamente en el modelo conceptual, pero **no hay un caso de uso que lo
  cambie** en este MVP — ver nota en sección 11).
- Relación formal `Instructor ↔ Club` (tabla puente estilo `owner_club`). Para el caso real (una
  academia, 4 instructores) no hace falta: el instructor ya queda acotado indirectamente por las
  canchas (`Court`) de sus `ClassSlot`s. Ver riesgo en sección 17 si en el futuro hay más de una
  academia por instalación.

## 4. Decisiones arquitectónicas

Estas son las reglas que gobiernan todo el resto del documento — no se listan de nuevo en cada
sección:

1. **Se mantiene la arquitectura hexagonal + Use Cases** ya presente en el proyecto:
   `domain.model` (records inmutables) → `domain.port.out` / `application.port.out` →
   `application.usecase.<dominio>` → `infrastructure.adapter` → `model.entity` →
   `persistence.repository` → `controller`. El módulo de clases se agrega como
   `application.usecase.instructor`, `application.usecase.classslot`,
   `application.usecase.classsession`, `application.usecase.classattendance` — **no** se introduce
   un patrón Controller→Service→Repository nuevo ni se toca el patrón existente.
2. **`Reservation` no se rediseña.** Su record, su máquina de estados (`StatusReservation`) y sus
   use cases existentes quedan intactos en su forma. La única intervención necesaria (sección 9)
   es aditiva: una tabla nueva y compartida que ambos flujos de escritura alimentan, no un cambio
   al modelo de `Reservation`.
3. **No se reutiliza `Schedule`/`ScheduleEntity`** para nada de este módulo — ese concepto sigue
   significando "franja de apertura de una cancha" exclusivamente. El nuevo concepto recurrente es
   `ClassSlot`.
4. **Un usuario, un rol** (`Rol` enum): se agrega `INSTRUCTOR` como cuarto valor. No se diseña
   multi-rol en este MVP — una persona que sea Instructor y Player a la vez necesitaría dos
   cuentas, igual que hoy pasaría entre `OWNER` y `PLAYER`. Esto **ya es una limitación existente
   del proyecto**, no una introducida por este módulo.
5. **Autenticación agnóstica al rol, confirmado en código**: `UserDetailsServiceImpl` carga
   `UserEntity` por email vía `UserRepository` (JOINED inheritance) sin conocer subtipos, y
   `UserMain.getAuthorities()` deriva la autoridad de `user.getRole().name()`. Agregar
   `InstructorEntity` como cuarto subtipo de `UserEntity` (mismo patrón que `OwnerEntity`/
   `PlayerEntity`, `@PrimaryKeyJoinColumn(name = "id")`) **no requiere tocar** `AuthController`,
   `AuthService`, `JwtFilter`, `JwtUtil` ni `UserDetailsServiceImpl`. Verificado leyendo el código,
   no asumido.
6. **Autorización por ownership contra la base**, patrón `@xAuthorization.isOwnerOfX(id,
   authentication)` (`CourtAuthorization`, `BranchAuthorization`, `ClubAuthorization`). Se agrega
   un bean nuevo para el bounded context de clases (sección 10).
7. **Concurrencia**: se reutiliza el patrón ya establecido — lock pesimista sobre el recurso
   disputado como primera lectura de la transacción (`findByIdForUpdate`) — donde el análisis de
   la sección 8 concluya que hace falta, y no en los lugares donde no hace falta.
8. **Excepciones de dominio nuevas** extienden `BusinessException` con su propio `HttpStatus`,
   igual que las ~35 existentes — cero cambios a `GlobalExceptionHandler`.
9. **Flyway sigue siendo la única fuente de verdad de esquema** fuera del perfil `dev`. Se
   verificó (no se asumió) el estado real:
   - `application.properties` (perfil default/producción):
     `spring.jpa.hibernate.ddl-auto=validate`, con Flyway como dueño del esquema.
   - `application-dev.properties` (perfil `dev`, opt-in explícito):
     `spring.jpa.hibernate.ddl-auto=update`, documentado con una advertencia explícita en el
     propio archivo de que **nunca** debe usarse fuera de desarrollo local.
   - **Conclusión: no hay ningún problema real que corregir hoy.** La configuración ya separa
     correctamente ambos perfiles. Esto contradice la premisa de la consigna de que hay "un
     problema existente" — se documenta la verificación en vez de inventar una corrección
     innecesaria (ver sección 12 para el detalle y sección 17 para dejarlo explícito).

## 5. Modelo de dominio

Records de dominio nuevos (estilo `record` + `create()` + métodos de transición, igual que
`Reservation`/`Court`):

```java
// enums/Level.java — nivel de la clase. Se deja como enum simple (no requiere comportamiento),
// mismo criterio que ClubType/ActiveStatus.
public enum Level { PRINCIPIANTE, INTERMEDIO, AVANZADO }

// enums/ClassAttendanceStatus.java — NOTA DE NOMBRE (ver más abajo): no "Status" a secas.
public enum ClassAttendanceStatus { PENDING, CONFIRMED, CANCELLED }

// enums/ClassSessionStatus.java — estado de la ocurrencia concreta, distinto del estado por alumno.
public enum ClassSessionStatus { SCHEDULED, CANCELLED }

// domain/model/ClassSlot.java
public record ClassSlot(
        Long id,
        Long instructorId,
        Long courtId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        Duration duration,
        Level level,
        int capacity,
        ActiveStatus status          // reutilizado — ver decisión más abajo
) {
    public static ClassSlot create(Long instructorId, Long courtId, DayOfWeek dayOfWeek,
                                    LocalTime startTime, Duration duration, Level level, int capacity) {
        if (capacity <= 0) throw new InvalidCapacityException("La capacidad debe ser mayor a cero");
        return new ClassSlot(null, instructorId, courtId, dayOfWeek, startTime, duration,
                level, capacity, ActiveStatus.ACTIVE);
    }

    public ClassSlot pause() {
        if (status == ActiveStatus.INACTIVE) throw new StatusAlreadyAppliedException("El horario ya está pausado");
        return withStatus(ActiveStatus.INACTIVE);
    }

    public ClassSlot reactivate() {
        if (status == ActiveStatus.ACTIVE) throw new StatusAlreadyAppliedException("El horario ya está activo");
        return withStatus(ActiveStatus.ACTIVE);
    }

    public boolean isActive() { return status == ActiveStatus.ACTIVE; }

    public boolean hasRoom(int activeEnrollmentCount) { return activeEnrollmentCount < capacity; }

    public boolean belongsTo(Long anInstructorId) { return instructorId.equals(anInstructorId); }

    private ClassSlot withStatus(ActiveStatus s) { return new ClassSlot(id, instructorId, courtId,
            dayOfWeek, startTime, duration, level, capacity, s); }
}

// domain/model/ClassEnrollment.java
public record ClassEnrollment(Long id, Long classSlotId, Long playerId, boolean active) {
    public static ClassEnrollment create(Long classSlotId, Long playerId) {
        return new ClassEnrollment(null, classSlotId, playerId, true);
    }
    public ClassEnrollment deactivate() { return new ClassEnrollment(id, classSlotId, playerId, false); }
}

// domain/model/ClassSession.java
// day/startTime/duration se copian del ClassSlot AL MOMENTO DE CREAR la sesión (snapshot),
// igual que Ticket congela precio/nombre de cancha al momento de reservar — una sesión ya
// creada no debe cambiar si el instructor edita el ClassSlot después.
public record ClassSession(
        Long id,
        Long classSlotId,
        Long courtId,           // copiado del ClassSlot al crear — evita un join para el chequeo de conflicto (sección 9)
        LocalDate day,
        LocalTime startTime,
        Duration duration,
        ClassSessionStatus status
) {
    public static ClassSession create(ClassSlot slot, LocalDate day) {
        var dateTime = LocalDateTime.of(day, slot.startTime());
        if (dateTime.isBefore(LocalDateTime.now())) {
            throw new InvalidTimeRangeException("No se puede crear una clase en una fecha pasada");
        }
        return new ClassSession(null, slot.id(), slot.courtId(), day, slot.startTime(),
                slot.duration(), ClassSessionStatus.SCHEDULED);
    }
}

// domain/model/ClassAttendance.java
public record ClassAttendance(Long id, Long classSessionId, Long playerId, ClassAttendanceStatus status) {
    public static ClassAttendance createPending(Long classSessionId, Long playerId) {
        return new ClassAttendance(null, classSessionId, playerId, ClassAttendanceStatus.PENDING);
    }
    public ClassAttendance confirm() { return withStatus(ClassAttendanceStatus.CONFIRMED); }
    public ClassAttendance cancel() { return withStatus(ClassAttendanceStatus.CANCELLED); }
    // Mismo patrón anti-IDOR que Reservation.belongsTo — listo para cuando el Player
    // confirme/cancele su propia asistencia (fuera de alcance de este MVP, ver sección 10).
    public boolean belongsTo(Long aPlayerId) { return playerId.equals(aPlayerId); }
    private ClassAttendance withStatus(ClassAttendanceStatus s) { return new ClassAttendance(id, classSessionId, playerId, s); }
}
```

**Dos desviaciones deliberadas respecto del modelo conceptual literal del usuario, explicadas para
que se acepten o se rechacen explícitamente — no se aplicaron en silencio:**

- El modelo conceptual llama `Status` al enum de `ClassAttendance`. Se propone
  `ClassAttendanceStatus` en su lugar, seguido de la convención ya existente en el proyecto donde
  **ningún** enum de estado se llama genérico (`StatusReservation`, `VerificationStatus`,
  `ActiveStatus`) — un `Status` a secas en el paquete `enums/` compartido sería ambiguo apenas se
  agregue el siguiente estado de algo. Es un cambio de nombre únicamente, cero cambio de
  semántica.
- `ClassSlot.status` reutiliza el `ActiveStatus` (`ACTIVE`/`INACTIVE`) ya existente en vez de un
  enum nuevo — "pausar/reactivar" un `ClassSlot` es exactamente la misma operación que
  "desactivar/activar" que ya existe para `Court`/`Branch`/`Club`. Reutilizar evita un enum
  redundante; se documenta por si el equipo prefiere un enum propio por claridad de dominio
  (`ClassSlotStatus{ACTIVE, PAUSED}` sería la alternativa, semánticamente idéntica).

## 6. Relaciones entre conceptos

```
Instructor (NUEVO, subtipo de UserEntity)
└── ClassSlot (NUEVO, 1:N — "todos los jueves 15:00, cancha 2, INTERMEDIO, cupo 4")
      ├── Court (existente, N:1 — la cancha ya definida en Club→Branch→Court)
      ├── ClassEnrollment (NUEVO, 1:N — el grupo fijo: qué Players pertenecen a este horario)
      │     └── Player (existente, N:1)
      └── ClassSession (NUEVO, 1:N — "jueves 10/09/2026 15:00", instancia concreta de una fecha)
            └── ClassAttendance (NUEVO, 1:N — por cada Player activo en el ClassEnrollment
                  al momento de crear la sesión)
                  └── Player (existente, N:1)
```

Por qué la separación en cuatro conceptos sigue siendo correcta para el MVP (no es
sobre-ingeniería, es la separación mínima que el flujo real de WhatsApp ya usa):

- **`ClassSlot` vs `ClassSession`** — exactamente la distinción plantilla/instancia que pidió el
  usuario: *"todos los jueves a las 15:00"* (`ClassSlot`, sin fecha) vs *"jueves 10/09/2026 a las
  15:00"* (`ClassSession`, con fecha concreta). Sin esta separación no hay forma de tener un grupo
  recurrente estable sin recrear la configuración cada semana, ni de tener el historial de qué pasó
  cada semana en particular.
- **`ClassEnrollment` vs `ClassAttendance`** — esta es la separación que replica fielmente lo que
  el profesor ya hace por WhatsApp, y es la razón por la que **no alcanza con una sola tabla**:
  - `ClassEnrollment` es la respuesta a *"¿quién es parte de este grupo de los jueves,
    en general?"* — cambia poco, casi siempre por decisión manual del instructor (alguien se suma
    o se da de baja del grupo fijo).
  - `ClassAttendance` es la respuesta a *"¿quién viene ESTA semana puntual?"* — cambia todas las
    semanas, es lo que hoy el instructor pregunta por WhatsApp antes de cada clase.
  - Ejemplo concreto que justifica la separación: Pedro es parte fijo del grupo de los jueves
    (`ClassEnrollment.active = true`, permanece así indefinidamente) pero esta semana avisó que no
    va (`ClassAttendance.status = CANCELLED` **solo para esa `ClassSession`**). La próxima semana
    se genera una `ClassAttendance` nueva en `PENDING` para Pedro, sin tocar su `ClassEnrollment`.
    Si existiera una sola tabla, cancelar por una semana y dar de baja definitiva serían la misma
    operación — lo cual es exactamente el bug conceptual que se evita separando ambos conceptos.
- Al crear una `ClassSession`, el use case correspondiente (`CreateClassSessionUseCase`, sección
  11) **genera automáticamente** una `ClassAttendance` en `PENDING` por cada `ClassEnrollment`
  activo de ese `ClassSlot` — es el único punto de acoplamiento entre ambos pares, y es
  intencional: nunca se agrega un alumno "suelto" a una sesión puntual sin pasar antes por el
  grupo fijo (eso sería la funcionalidad de invitado ocasional, explícitamente fuera de alcance,
  sección 3).

**Conclusión: se valida el modelo de 4 conceptos tal como lo planteó el usuario**, con los dos
ajustes de nombre de enum señalados en la sección 5.

## 7. Reglas de negocio

1. La capacidad de un `ClassSlot` debe ser un entero positivo — inicial de referencia 4, pero
   **configurable por cada `ClassSlot`** (no una constante de aplicación ni una columna con
   default hardcodeado). Se valida en `ClassSlot.create()`.
2. La cantidad de `ClassEnrollment` activos de un `ClassSlot` no puede superar su `capacity` —
   regla enforced en `AddPlayerToClassSlotUseCase` (sección 11), no en el propio record
   `ClassSlot` (el record no conoce cuántos enrollments existen; eso es responsabilidad del use
   case, igual que `BookReservationUseCase` resuelve disponibilidad consultando el repositorio, no
   dentro de `Reservation`).
3. No se puede duplicar la inscripción de un mismo `Player` en el mismo `ClassSlot` (constraint a
   nivel de aplicación + `UNIQUE` a nivel de base, sección 12).
4. Dar de baja a un alumno de un `ClassSlot` es una baja lógica (`ClassEnrollment.active = false`),
   nunca un `DELETE` — mismo criterio ya documentado para `Reservation` en
   [PlayerEntity.java:25-28](../src/main/java/com/deportlink/deportlink/model/entity/PlayerEntity.java#L25)
   ("Reservation representa historial y no debe eliminarse"): las `ClassAttendance` ya generadas
   para sesiones pasadas son historial y no deben verse afectadas por la baja.
5. Una `ClassSession` sólo puede crearse a partir de un `ClassSlot` activo (`ACTIVE`, no pausado) y
   con fecha futura (mismo criterio que `TimeSlot.isFuture()`/`Reservation.create()`).
6. Al crear una `ClassSession`, se generan `ClassAttendance` en `PENDING` para todos los
   `ClassEnrollment` activos del `ClassSlot` en ese momento — no se agregan alumnos "sueltos" a una
   sesión puntual en este MVP (ver sección 6).
7. No puede existir más de una `ClassSession` para el mismo `ClassSlot` en la misma fecha (`UNIQUE
   (class_slot_id, session_date)`) — evita duplicar la clase del jueves dos veces por error.
8. Transición de estado de `ClassAttendance`: se permite libremente entre `PENDING`, `CONFIRMED` y
   `CANCELLED` en cualquier sentido (incluso volver de `CANCELLED` a `CONFIRMED`) mientras la
   `ClassSession` esté en `SCHEDULED`. **Decisión deliberadamente más permisiva que la máquina de
   estados de `Reservation`**: acá no hay ventana de cancelación ni cargo económico de por medio,
   es un registro manual de una conversación de WhatsApp que puede cambiar de opinión varias veces
   antes de la clase — no una garantía transaccional de cupo pago. Se documenta como decisión, no
   como omisión.
9. Ninguna operación de escritura sobre `ClassSlot`/`ClassSession`/`ClassAttendance` puede crear un
   conflicto de cancha con una `Reservation` existente, y viceversa (sección 9 — regla crítica).
10. `ClassSession.status` existe en el modelo (`SCHEDULED`/`CANCELLED`) pero **ningún caso de uso
    de este MVP lo cambia** — se deja preparado para una futura `CancelClassSessionUseCase` sin
    necesitar otra migración de esquema. Ver nota de alcance en sección 3.

## 8. Concurrencia y capacidad

**Qué significa "capacidad máxima"**: el límite de alumnos que integran el `ClassEnrollment`
(grupo fijo) de un `ClassSlot`. No es un límite por `ClassSession` — la ocupación mostrada por
sesión ("3/4 alumnos") es informativa (cuántos del grupo fijo siguen activos para esa fecha
puntual, es decir, `ClassAttendance != CANCELLED`), pero el techo duro (`capacity`) se aplica al
tamaño del grupo (`ClassEnrollment`), no a la cuenta de asistencias confirmadas de una sesión.

**Quién puede agregar alumnos**: el instructor dueño del `ClassSlot`, o `ADMIN` — nunca el propio
`Player` en este MVP (autoservicio de inscripción está fuera de alcance, sección 3).

**Qué sucede si se intenta superar la capacidad**: `AddPlayerToClassSlotUseCase` rechaza la
operación con una excepción de dominio nueva (`ClassSlotFullException extends BusinessException`,
`HttpStatus.CONFLICT` — mismo criterio que `SlotNotAvailableException`).

**Qué sucede si dos operaciones intentan ocupar el último lugar simultáneamente**: es el mismo
problema de fondo que ya resuelve `BookReservationUseCase` para reservas, pero con una diferencia
de contexto importante — **la probabilidad real de esta carrera en el MVP es baja**: quien escribe
es siempre un instructor humano operando desde su celular, de a un alumno por vez, nunca alumnos
compitiendo entre sí por su propio lugar (eso solo aparecería el día que se habilite autoservicio
de `Player`, fuera de alcance). Aun así, **se recomienda blindarlo desde ya** con el mismo patrón
ya establecido en el proyecto, por dos razones: (a) consistencia arquitectónica — es el mismo
patrón que ya existe, no cuesta más escribirlo bien la primera vez; (b) deja la puerta cerrada de
antemano para cuando (Etapa futura) un `Player` pueda autoinscribirse, momento en que la carrera sí
sería real y frecuente.

**¿Se necesita lock pesimista en el MVP?** Sí, pero acotado: `AddPlayerToClassSlotUseCase` debe
adquirir `findByIdForUpdate` sobre el `ClassSlot` **como primera lectura de la transacción**
(mismo patrón que `AddScheduleUseCase` bloquea `Court` antes de leer sus horarios existentes),
contar los `ClassEnrollment` activos, y recién ahí decidir si hay lugar. Esto requiere agregar
`ClassSlotRepositoryPort.findByIdForUpdate(Long id)`, análogo a
`CourtRepositoryPort.findByIdForUpdate`.

**Dónde vive la regla, según la arquitectura actual**: el conteo y la decisión de "hay lugar o no"
se resuelven en el use case (`AddPlayerToClassSlotUseCase`), no en el record `ClassSlot` — el
record expone `hasRoom(int activeEnrollmentCount)` como función pura (recibe el conteo ya resuelto,
igual que `Reservation.cancel()` recibe `cancellationWindowHours` ya resuelto por el use case en
vez de conocer `Branch`). El conteo en sí vive en el adapter de persistencia
(`ClassEnrollmentRepositoryAdapter.countActiveByClassSlotId`).

No hace falta ningún lock para `ClassAttendance` (confirmar/cancelar asistencia): es una
transición de estado 1:1 sobre una fila ya existente, sin ninguna disputa por un recurso
compartido — mismo criterio que ya aplica el proyecto para no bloquear en
`CancelReservationUseCase`.

## 9. Conflicto Reservation vs ClassSession

Punto crítico, analizado con el caso concreto pedido:

> `Court 2`, `10/09/2026`, `15:00–16:00` — existe una `ClassSession`. ¿Qué pasa si alguien intenta
> crear una `Reservation` para esa misma cancha/horario? Y al revés.

**Por qué el mecanismo actual no alcanza tal cual**: la garantía de "no doble reserva" hoy vive
**dentro de la tabla `reservation`** — el índice único `uq_reservation_active_slot` (migración
[V2](../src/main/resources/db/migration/V2__add_unique_reservation_slot.sql)) es una columna
generada (`active_slot_court_id`) más un `UNIQUE (active_slot_court_id, reservation_day,
start_time)`. MySQL no permite un índice `UNIQUE` que abarque dos tablas distintas
(`reservation` y la nueva `class_session`), así que **ese mismo mecanismo no puede extenderse
directamente** para cubrir clases.

**Propuesta concreta para el MVP — tabla compartida `court_occupancy`**, defensa en profundidad de
dos capas, siguiendo el mismo espíritu que ya aplica el proyecto (lock de aplicación + constraint
de base como red de seguridad):

```
court_occupancy
  id              BIGINT PK
  court_id        BIGINT NOT NULL (FK → court.id)
  occupied_day    DATE NOT NULL
  start_time      TIME NOT NULL
  source_type     ENUM('RESERVATION','CLASS_SESSION') NOT NULL
  source_id       BIGINT NOT NULL

  UNIQUE (court_id, occupied_day, start_time)
```

- **Capa 1 — lock de aplicación**: tanto `BookReservationUseCase` como el nuevo
  `CreateClassSessionUseCase` ya deben (el primero lo hace hoy) tomar `findByIdForUpdate` sobre la
  `Court` como primera lectura de su transacción. Con la `Court` bloqueada, cada uno consulta
  `court_occupancy` para ese `court_id`+`day`+`startTime` antes de insertar — si ya hay una fila
  (de cualquier `source_type`), rechaza con una excepción de conflicto.
- **Capa 2 — constraint de base**: el `UNIQUE (court_id, occupied_day, start_time)` en
  `court_occupancy` es la red de seguridad si, por lo que sea, dos transacciones llegaran a
  insertar sin haber tomado el lock correctamente (bug futuro, código nuevo que no respete el
  orden) — el segundo `INSERT` falla con `DataIntegrityViolationException`, que
  `GlobalExceptionHandler` ya traduce a `409 Conflict` de forma genérica, sin ningún cambio.

**Flujo en ambos sentidos:**

- **Ya existe `ClassSession`, se intenta crear `Reservation`**: `BookReservationUseCase` (código
  existente) necesita un cambio puntual y acotado — después de tomar el lock de `Court`, antes de
  emitir el `Ticket`, insertar en `court_occupancy` con `source_type='RESERVATION'`; si el insert
  choca contra la fila que la `ClassSession` ya escribió, se traduce a la excepción ya existente
  `SlotNotAvailableException` ("el horario ya está ocupado"). **Este es el único touch a código de
  `Reservation` que este diseño requiere** — no toca el record `Reservation`, no toca
  `StatusReservation`, no toca la lógica de cancelación/reprogramación existente; es un efecto
  colateral adicional en el use case, del mismo tamaño que el que ya existe hoy al emitir el
  `Ticket`.
- **Ya existe `Reservation`, se intenta crear `ClassSession`**: `CreateClassSessionUseCase` (nuevo)
  hace la misma verificación en sentido inverso — bloquea `Court`, consulta `court_occupancy`
  (o, en su defecto, si por alguna razón la reserva es anterior a la existencia de esta tabla y no
  tiene fila ahí — ver limitación abajo — consulta también `ReservationRepositoryPort` directamente
  como chequeo adicional de transición) y rechaza con una excepción nueva
  (`CourtSlotOccupiedException extends BusinessException`, `409`) si hay conflicto.
- **Cancelación/reprogramación**: `CancelReservationUseCase` y `RescheduleReservationUseCase`
  (existentes) deben borrar la fila de `court_occupancy` correspondiente al cancelar, e
  insertar una nueva al reprogramar — mismo tipo de efecto colateral acotado que el punto anterior.

**Limitaciones a documentar explícitamente (no se ocultan):**

1. **Solo detecta coincidencia exacta de `(court_id, day, start_time)`**, no solapamiento real de
   intervalos (una clase de 15:00 a 16:00 y una reserva de 15:30 a 16:30 en teoría podrían no
   coincidir en el `start_time` exacto y pasar ambas validaciones). **Esta es una simplificación
   que el proyecto ya acepta hoy** para reservas entre sí (el propio índice `V2` es por
   `start_time` exacto, no por rango) — no es una limitación nueva introducida por este diseño,
   es la misma que ya existe, extendida al nuevo caso.
2. **Depende de la disciplina de lockeo**: si en el futuro alguien agrega un tercer camino de
   escritura sobre `Court` (otra forma de crear un `Reservation` o una `ClassSession`) sin respetar
   "lockear `Court` primero, después tocar `court_occupancy`", la Capa 1 deja de proteger — solo
   queda la Capa 2 (el `UNIQUE`), que sí protege siempre porque es un constraint de base, pero
   entrega un 500/409 en vez de una validación prolija con mensaje de negocio.
3. **Requiere backfill** al migrar: las `Reservation` `RESERVADO` ya existentes en producción deben
   volcarse a `court_occupancy` en la misma migración que crea la tabla (mismo criterio que la
   migración `V3` hizo backfill de `cancellation_window_hours`), para que el `UNIQUE` no rompa
   contra datos reales el primer día.
4. **No cubre un DELETE/INSERT manual directo en la base** (fuera de la aplicación) — es una
   garantía a nivel de aplicación + constraint de base, no una garantía absoluta ante cualquier
   acceso a la base.

Esta es una tabla nueva y pequeña, no una generalización de `Reservation` — cumple la restricción
explícita de "sin introducir una gran refactorización".

## 10. Seguridad y ownership

**`INSTRUCTOR`** (rol nuevo en `Rol`, con `InstructorEntity extends UserEntity` — mismo patrón
`@PrimaryKeyJoinColumn(name = "id")` que `OwnerEntity`/`PlayerEntity`; sin columnas propias
adicionales en el MVP, igual que `PlayerEntity` no agrega ninguna más allá de sus relaciones):

- Ve únicamente sus propios `ClassSlot`/`ClassSession`/`ClassAttendance` — nunca los de otro
  instructor, aunque adivine el id (ownership contra la base, no contra el token).
- Gestiona (crea/pausa/reactiva) únicamente sus propios `ClassSlot`.
- Gestiona (agrega/quita) alumnos únicamente en sus propios `ClassSlot`.
- Confirma/cancela asistencia únicamente en `ClassSession`s de sus propios `ClassSlot`.

**`ADMIN`**: acceso global de lectura y gestión sobre todo lo anterior — mismo patrón
`hasRole('ADMIN') or @xAuthorization....` ya usado en cada controller existente.

**`PLAYER`**: sin panel de clases en este MVP. El modelo ya deja `ClassAttendance.belongsTo(Long)`
listo (mismo patrón que `Reservation.belongsTo`) para que, en una etapa futura, un endpoint
`PATCH /api/class-attendances/{id}/confirm` con `@PreAuthorize("hasRole('PLAYER') and
@classAuthorization.isOwnAttendance(#id, authentication)")` reutilice el mismo caso de uso
(`ConfirmAttendanceUseCase`) que usa el instructor — **el dominio no necesita cambiar** cuando esa
etapa llegue, solo un controller/autorización nuevos, tal como pidió el usuario.

**Bean de autorización propuesto** — se agrupan las tres verificaciones de ownership de este
módulo en un solo bean `classAuthorization` (en vez de tres beans separados como
`Court`/`Branch`/`Club`), porque los tres recursos (`ClassSlot`, `ClassSession`, `ClassAttendance`)
pertenecen al mismo bounded context y todos resuelven ownership por el mismo camino
(`instructor_id`), a diferencia de `Court`/`Branch`/`Club` que son tres agregados independientes
entre sí. Es una desviación menor y deliberada del "un bean por agregado", documentada para que se
confirme o se prefiera separarlos:

```java
@Component("classAuthorization")
public class ClassAuthorization {
    // SELECT ... FROM class_slot WHERE id = :idSlot AND instructor_id = :idInstructor
    boolean isOwnerOfClassSlot(long idClassSlot, Authentication authentication);

    // join class_session -> class_slot, mismo patrón que
    // CourtRepository.existsByCourtAndOwner (join a través de la jerarquía)
    boolean isOwnerOfClassSession(long idClassSession, Authentication authentication);

    // preparado para la etapa futura de Player — no usado por ningún endpoint de este MVP
    boolean isOwnAttendance(long idAttendance, Authentication authentication);
}
```

**Alta de instructores**: se propone el mismo patrón que `Owner` — alta exclusiva por `ADMIN`
(`POST /api/instructors`, `hasRole('ADMIN')`, sin autoregistro), no autoservicio como `Player`.
Es una decisión razonable por defecto (un instructor es personal de la academia, no un usuario
anónimo de internet) pero **no está explícitamente confirmada por el usuario** — se lista también
en la sección 17.

## 11. Casos de uso

Convención: un caso de uso = una operación de negocio, en
`application.usecase.{instructor,classslot,classsession,classattendance}`.

---

**`RegisterInstructorUseCase`**
- Responsabilidad: dar de alta un instructor con credenciales propias.
- Actor: `ADMIN`.
- Entrada: `InstructorRequestDto` (nombre, apellido, email, teléfono, password).
- Salida: `Instructor` (dominio).
- Reglas: email único (constraint ya existente en `users.email`); password se hashea con
  `PasswordEncoder` (reutilizado, igual que `RegisterOwnerUseCase`).
- Autorización: `hasRole('ADMIN')`.
- Errores: `InstructorAlreadyExistsException` (email duplicado).

**`CreateClassSlotUseCase`**
- Responsabilidad: crear la plantilla recurrente de una clase.
- Actor: `INSTRUCTOR` (se crea para sí mismo — `instructorId` viene de `@CurrentUserId`, nunca del
  body, mismo criterio anti-IDOR que `BookReservationUseCase` con `playerId`) o `ADMIN` (recibe el
  `instructorId` explícito en el body).
- Entrada: `courtId`, `dayOfWeek`, `startTime`, `duration`, `level`, `capacity`.
- Salida: `ClassSlot`.
- Reglas: `capacity > 0`; la `Court` debe existir y estar activa (mismo chequeo que
  `AddScheduleUseCase` hace sobre `Court`/`Branch`).
- Autorización: `hasRole('ADMIN') or hasRole('INSTRUCTOR')`.
- Errores: `CourtNotFoundException`, `InvalidCapacityException`, `BranchNotActiveException`.

**`PauseClassSlotUseCase`** / **`ReactivateClassSlotUseCase`**
- Responsabilidad: encender/apagar la generación de nuevas `ClassSession`s para un `ClassSlot`,
  sin afectar el `ClassEnrollment` ni las `ClassSession`s ya creadas.
- Actor: `INSTRUCTOR` dueño, o `ADMIN`.
- Entrada: `classSlotId`.
- Salida: `ClassSlot` actualizado.
- Reglas: no pausar algo ya pausado ni reactivar algo ya activo (`StatusAlreadyAppliedException`,
  mismo patrón que `ActivateCourtUseCase`/`DeactivateCourtUseCase`).
- Autorización: `hasRole('ADMIN') or (hasRole('INSTRUCTOR') and
  @classAuthorization.isOwnerOfClassSlot(#id, authentication))`.
- Errores: `ClassSlotNotFoundException`, `StatusAlreadyAppliedException`.

**`AddPlayerToClassSlotUseCase`**
- Responsabilidad: sumar un `Player` al grupo fijo de un `ClassSlot`.
- Actor: `INSTRUCTOR` dueño, o `ADMIN`.
- Entrada: `classSlotId`, `playerId`.
- Salida: `ClassEnrollment`.
- Reglas: lock pesimista sobre `ClassSlot` (sección 8); rechaza si ya está lleno
  (`ClassSlotFullException`) o si el `Player` ya está activo en ese `ClassSlot`
  (`PlayerAlreadyEnrolledException`) — si estaba inactivo (baja previa), se reactiva la fila en
  vez de insertar una nueva (evita duplicar historial).
- Autorización: igual que `PauseClassSlotUseCase`.
- Errores: `ClassSlotNotFoundException`, `PlayerNotFoundException`, `ClassSlotFullException`,
  `PlayerAlreadyEnrolledException`.

**`RemovePlayerFromClassSlotUseCase`**
- Responsabilidad: dar de baja (lógica) a un `Player` del grupo fijo.
- Actor: `INSTRUCTOR` dueño, o `ADMIN`.
- Entrada: `classSlotId`, `playerId`.
- Salida: `void` / `ClassEnrollment` desactivado.
- Reglas: baja lógica (`active = false`), nunca `DELETE` (sección 7, regla 4); no afecta
  `ClassAttendance` ya generadas.
- Autorización: igual que las anteriores.
- Errores: `ClassEnrollmentNotFoundException`.

**`GetClassSlotsByInstructorUseCase`**
- Responsabilidad: listar los `ClassSlot` de un instructor (paso "ve sus clases" del flujo, a
  nivel de configuración).
- Actor: `INSTRUCTOR` (propio, vía `@CurrentUserId`) o `ADMIN` (cualquier instructor).
- Entrada: `instructorId` (resuelto por token para `INSTRUCTOR`, explícito para `ADMIN`).
- Salida: `List<ClassSlot>`.
- Reglas: ninguna de negocio — es una consulta.
- Autorización: `hasRole('ADMIN') or (hasRole('INSTRUCTOR') and #instructorId == principal.id)`
  (mismo patrón que `ReservationController.getByPlayer`).
- Errores: ninguno esperado (lista vacía si no tiene `ClassSlot`s).

**`CreateClassSessionUseCase`**
- Responsabilidad: materializar la ocurrencia concreta de una fecha a partir de un `ClassSlot`, y
  generar las `ClassAttendance` iniciales.
- Actor: `INSTRUCTOR` dueño del `ClassSlot`, o `ADMIN`.
- Entrada: `classSlotId`, `day` (fecha).
- Salida: `ClassSession` (con sus `ClassAttendance` ya generadas).
- Reglas: el `ClassSlot` debe estar `ACTIVE`; la fecha debe ser futura y debe coincidir con el
  `dayOfWeek` del `ClassSlot` (o se rechaza — evita crear "el jueves" un lunes por error); no debe
  existir ya una `ClassSession` para ese `ClassSlot`+fecha (`UNIQUE`, sección 7 regla 7);
  **validación de conflicto de cancha contra `court_occupancy`** (sección 9) — es la primera
  lectura de la transacción, bloqueando `Court`; se genera una `ClassAttendance` en `PENDING` por
  cada `ClassEnrollment` activo del `ClassSlot`.
- Autorización: igual que `PauseClassSlotUseCase`.
- Errores: `ClassSlotNotFoundException`, `ClassSlotNotActiveException`, `InvalidTimeRangeException`
  (fecha pasada o día de semana no coincide), `ClassSessionAlreadyExistsException`,
  `CourtSlotOccupiedException` (conflicto con `Reservation` u otra `ClassSession`).

**`GetClassSessionsByInstructorUseCase`**
- Responsabilidad: listar las `ClassSession`s del instructor (paso "ve sus clases" del flujo, a
  nivel de agenda concreta — lo que realmente se ve en el panel: "Jueves 15:00, INTERMEDIO, Cancha
  2, 3/4").
- Actor: `INSTRUCTOR` (propio) o `ADMIN`.
- Entrada: `instructorId`, rango de fechas opcional (por defecto, próximas).
- Salida: `List<ClassSessionSummary>` — proyección liviana con ocupación ya calculada (`3/4`),
  para no forzar al frontend a calcularla (mobile-first: menos round-trips).
- Reglas: ninguna de negocio.
- Autorización: igual que `GetClassSlotsByInstructorUseCase`.
- Errores: ninguno esperado.

**`GetClassSessionDetailUseCase`**
- Responsabilidad: traer el detalle completo de una `ClassSession` — cancha, nivel, capacidad,
  ocupación, y la lista de alumnos con su estado (el "Juan CONFIRMED / María CONFIRMED / Pedro
  CANCELLED / Lucía PENDING" del flujo).
- Actor: `INSTRUCTOR` dueño, o `ADMIN`.
- Entrada: `classSessionId`.
- Salida: `ClassSessionDetail` (sesión + cancha + nivel + capacidad + lista de
  `ClassAttendance` con nombre de `Player` resuelto).
- Reglas: ninguna de negocio — es una consulta, pero con ownership.
- Autorización: `hasRole('ADMIN') or (hasRole('INSTRUCTOR') and
  @classAuthorization.isOwnerOfClassSession(#id, authentication))`.
- Errores: `ClassSessionNotFoundException` (también se usa para ocultar sesiones ajenas — ver nota
  IDOR abajo).

**`ConfirmAttendanceUseCase`**
- Responsabilidad: marcar a un alumno como confirmado para una `ClassSession` concreta. Es también
  la acción detrás de "Simular confirmación de WhatsApp" (sección 16 — no hay un use case
  separado para la simulación).
- Actor: `INSTRUCTOR` dueño de la `ClassSession` (vía su `ClassSlot`), o `ADMIN`.
- Entrada: `classAttendanceId`.
- Salida: `ClassAttendance` actualizada.
- Reglas: transición libre entre estados mientras la `ClassSession` esté `SCHEDULED` (sección 7,
  regla 8) — no valida "ya estaba confirmado" como error, es idempotente (a diferencia de
  `ActivateCourtUseCase`, que sí rechaza duplicar el estado; se documenta la diferencia porque acá
  no genera ningún efecto colateral problemático confirmar dos veces).
- Autorización: igual que `GetClassSessionDetailUseCase`.
- Errores: `ClassAttendanceNotFoundException`.

**`CancelAttendanceUseCase`**
- Responsabilidad: marcar a un alumno como cancelado para una `ClassSession` concreta — libera su
  lugar en la ocupación mostrada (`3/4` → `2/4`), sin tocar su `ClassEnrollment`.
- Actor: `INSTRUCTOR` dueño, o `ADMIN`.
- Entrada: `classAttendanceId`.
- Salida: `ClassAttendance` actualizada.
- Reglas: igual que `ConfirmAttendanceUseCase`.
- Autorización: igual.
- Errores: `ClassAttendanceNotFoundException`.

---

**Casos de uso evaluados y descartados para este MVP** (para que quede explícito por qué no están):

- *Editar un `ClassSlot` existente* (cambiar cancha/hora/nivel/capacidad): no está en el flujo
  pedido por el usuario; el reemplazo natural en el MVP es pausar y crear uno nuevo. Se deja fuera
  a propósito — agregar `UpdateClassSlotUseCase` es trivial de sumar después sin romper nada.
- *Eliminar (hard delete) un `ClassSlot`/`ClassSession`*: contradice la regla de historial (sección
  7, regla 4); no se diseña.
- *Cancelar una `ClassSession` completa*: el campo de estado existe (sección 5) pero el caso de uso
  no se construye en este MVP (sección 3) — no estaba en la lista de casos de uso pedida.

## 12. Persistencia y base de datos

**Estado de `ddl-auto` + Flyway — verificado, no asumido** (ver decisión 9 en sección 4): no hace
falta corregir nada antes de sumar las migraciones nuevas. `application.properties` ya usa
`validate` (Flyway manda) y `application-dev.properties` ya aísla `update` al perfil `dev` con una
advertencia explícita de que no debe usarse fuera de desarrollo local. El flujo de trabajo para
este módulo es el mismo que ya documenta el proyecto: iterar localmente con el perfil `dev`
(Hibernate ajusta el esquema automáticamente) y, antes de mergear, escribir a mano la migración
Flyway real (`V4`, `V5`, ...) que refleje esos cambios — igual que se hizo para `V1`–`V3`.

**Tablas nuevas** (numeración de migraciones sugerida, una por cambio incremental, continuando
`V3`):

```sql
-- V4: rol INSTRUCTOR + tabla instructors
ALTER TABLE users MODIFY COLUMN role ENUM('ADMIN','OWNER','PLAYER','INSTRUCTOR') DEFAULT NULL;

CREATE TABLE instructors (
    id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_instructors_users FOREIGN KEY (id) REFERENCES users(id)
);

-- V5: class_slot
CREATE TABLE class_slot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    instructor_id BIGINT NOT NULL,
    court_id BIGINT NOT NULL,
    day_of_week ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    start_time TIME NOT NULL,
    duration BIGINT NOT NULL,               -- reutiliza DurationConverter existente
    level ENUM('PRINCIPIANTE','INTERMEDIO','AVANZADO') NOT NULL,
    capacity INT NOT NULL,
    active_status ENUM('ACTIVE','INACTIVE') NOT NULL,   -- reutiliza ActiveStatus (sección 5)
    PRIMARY KEY (id),
    KEY idx_class_slot_instructor (instructor_id),
    KEY idx_class_slot_court_day (court_id, day_of_week),
    CONSTRAINT fk_class_slot_instructor FOREIGN KEY (instructor_id) REFERENCES instructors(id),
    CONSTRAINT fk_class_slot_court FOREIGN KEY (court_id) REFERENCES court(id)
);

-- V6: class_enrollment
CREATE TABLE class_enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_slot_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_enrollment_slot_player (class_slot_id, player_id),
    KEY idx_class_enrollment_slot (class_slot_id),
    KEY idx_class_enrollment_player (player_id),
    CONSTRAINT fk_class_enrollment_slot FOREIGN KEY (class_slot_id) REFERENCES class_slot(id),
    CONSTRAINT fk_class_enrollment_player FOREIGN KEY (player_id) REFERENCES players(id)
);

-- V7: class_session
CREATE TABLE class_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_slot_id BIGINT NOT NULL,
    court_id BIGINT NOT NULL,               -- copiado del ClassSlot al crear (snapshot, sección 5)
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    duration BIGINT NOT NULL,
    status ENUM('SCHEDULED','CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_session_slot_date (class_slot_id, session_date),
    KEY idx_class_session_court_date (court_id, session_date, start_time),
    CONSTRAINT fk_class_session_slot FOREIGN KEY (class_slot_id) REFERENCES class_slot(id),
    CONSTRAINT fk_class_session_court FOREIGN KEY (court_id) REFERENCES court(id)
);

-- V8: class_attendance
CREATE TABLE class_attendance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_session_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    status ENUM('PENDING','CONFIRMED','CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_attendance_session_player (class_session_id, player_id),
    KEY idx_class_attendance_session (class_session_id),
    KEY idx_class_attendance_player (player_id),
    CONSTRAINT fk_class_attendance_session FOREIGN KEY (class_session_id) REFERENCES class_session(id),
    CONSTRAINT fk_class_attendance_player FOREIGN KEY (player_id) REFERENCES players(id)
);

-- V9: court_occupancy (conflicto Reservation vs ClassSession, sección 9) + backfill
CREATE TABLE court_occupancy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    court_id BIGINT NOT NULL,
    occupied_day DATE NOT NULL,
    start_time TIME NOT NULL,
    source_type ENUM('RESERVATION','CLASS_SESSION') NOT NULL,
    source_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_court_occupancy_slot (court_id, occupied_day, start_time),
    CONSTRAINT fk_court_occupancy_court FOREIGN KEY (court_id) REFERENCES court(id)
);

INSERT INTO court_occupancy (court_id, occupied_day, start_time, source_type, source_id)
SELECT court_id, reservation_day, start_time, 'RESERVATION', id
FROM reservation
WHERE status = 'RESERVADO';
```

**Relaciones / claves — resumen**:

| Tabla | FK salientes | UNIQUE | Índices |
|---|---|---|---|
| `instructors` | `id → users.id` | — | — |
| `class_slot` | `instructor_id → instructors.id`, `court_id → court.id` | — | `(instructor_id)`, `(court_id, day_of_week)` |
| `class_enrollment` | `class_slot_id → class_slot.id`, `player_id → players.id` | `(class_slot_id, player_id)` | `(class_slot_id)`, `(player_id)` |
| `class_session` | `class_slot_id → class_slot.id`, `court_id → court.id` | `(class_slot_id, session_date)` | `(court_id, session_date, start_time)` |
| `class_attendance` | `class_session_id → class_session.id`, `player_id → players.id` | `(class_session_id, player_id)` | `(class_session_id)`, `(player_id)` |
| `court_occupancy` | `court_id → court.id` | `(court_id, occupied_day, start_time)` | — (la UNIQUE ya sirve como índice) |

## 13. API

Mobile-first: los endpoints de lectura devuelven proyecciones ya armadas para pantalla (ocupación
calculada, nombre del alumno resuelto) en vez de forzar al cliente a combinar varias respuestas.

| Método | Endpoint | Actor | Request | Response | Autorización | Errores principales |
|---|---|---|---|---|---|---|
| `POST` | `/api/instructors` | Admin | `InstructorRequestDto` | `201` `InstructorResponseDto` | `hasRole('ADMIN')` | `400`, `409` email duplicado |
| `GET` | `/api/instructors/my-profile` | Instructor | — | `InstructorResponseDto` | `hasRole('INSTRUCTOR')` | `401` |
| `POST` | `/api/class-slots` | Instructor / Admin | `ClassSlotRequestDto` | `201` `ClassSlotResponseDto` | `hasRole('ADMIN') or hasRole('INSTRUCTOR')` | `400`, `404` court |
| `PATCH` | `/api/class-slots/{id}/pause` | Instructor dueño / Admin | — | `200` mensaje | ownership | `403/404`, `409` ya pausado |
| `PATCH` | `/api/class-slots/{id}/reactivate` | ídem | — | `200` mensaje | ownership | ídem |
| `POST` | `/api/class-slots/{id}/players` | Instructor dueño / Admin | `{ playerId }` | `201` `ClassEnrollmentResponseDto` | ownership | `404` player, `409` lleno / ya inscripto |
| `DELETE` | `/api/class-slots/{id}/players/{playerId}` | ídem | — | `200` mensaje | ownership | `404` |
| `GET` | `/api/class-slots/mine` | Instructor | — | `List<ClassSlotResponseDto>` | `hasRole('INSTRUCTOR')` | — |
| `GET` | `/api/instructors/{id}/class-slots` | Admin | — | `List<ClassSlotResponseDto>` | `hasRole('ADMIN')` | `404` |
| `POST` | `/api/class-slots/{id}/sessions` | Instructor dueño / Admin | `{ day }` | `201` `ClassSessionResponseDto` | ownership | `400` fecha/día inválido, `409` ya existe / conflicto de cancha |
| `GET` | `/api/class-sessions/mine` | Instructor | `?from=&to=` (opcional) | `List<ClassSessionSummaryDto>` (con ocupación `x/y`) | `hasRole('INSTRUCTOR')` | — |
| `GET` | `/api/class-sessions/{id}` | Instructor dueño / Admin | — | `ClassSessionDetailDto` (cancha, nivel, capacidad, alumnos+estado) | ownership | `403/404` |
| `PATCH` | `/api/class-attendances/{id}/confirm` | Instructor dueño / Admin | — | `200` `ClassAttendanceResponseDto` | ownership (vía sesión) | `403/404` |
| `PATCH` | `/api/class-attendances/{id}/cancel` | ídem | — | `200` `ClassAttendanceResponseDto` | ownership | `403/404` |

**Nota anti-IDOR**: igual que `CancelReservationUseCase` (404 en vez de 403 para no revelar
existencia de un recurso ajeno), `GetClassSessionDetailUseCase` y las acciones de
confirmar/cancelar deberían devolver `ClassSessionNotFoundException`/`ClassAttendanceNotFoundException`
(404) cuando un instructor intenta acceder al recurso de otro instructor, no un 403 explícito —
decisión a confirmar con el equipo (documentada como recomendación, no como cierre absoluto, en la
sección 17).

## 14. Testing

Priorizado — lo que realmente protege este MVP, no una lista exhaustiva por completitud:

**1. Tests de dominio (prioridad alta)**
- `ClassSlot.create()` rechaza `capacity <= 0`.
- `ClassSlot.pause()`/`reactivate()` rechazan transición redundante (`StatusAlreadyAppliedException`).
- `ClassSession.create()` rechaza fecha pasada.
- `ClassAttendance` permite transición libre entre los tres estados (documentando la decisión de
  la sección 7, regla 8, con un test que la deja explícita en vez de implícita).

**2. Tests de use cases (prioridad alta — son los que protegen las reglas de negocio reales)**
- `AddPlayerToClassSlotUseCase`: rechaza superar `capacity`; rechaza doble inscripción activa;
  reactiva una inscripción dada de baja en vez de duplicarla.
- `CreateClassSessionUseCase`: genera una `ClassAttendance` `PENDING` por cada `ClassEnrollment`
  activo; rechaza `ClassSlot` pausado; rechaza fecha que no coincide con el `dayOfWeek`; rechaza
  duplicar sesión para la misma fecha; **rechaza conflicto de cancha contra una `Reservation`
  existente** (el test más importante de todo el módulo, sección 9).
- `BookReservationUseCase` (test de regresión sobre código EXISTENTE): rechaza reservar una cancha
  que ya tiene una `ClassSession` en ese horario — este test no existe hoy y es obligatorio antes
  de dar por cerrado el MVP, porque valida el cambio hecho al use case existente.
- `ConfirmAttendanceUseCase`/`CancelAttendanceUseCase`: transición correcta, idempotencia de
  confirmar dos veces.

**3. Tests de seguridad/ownership (prioridad alta)**
- Un `INSTRUCTOR` no puede pausar/agregar alumnos/ver el detalle de un `ClassSlot`/`ClassSession`
  de otro instructor (403 o 404 según lo que se confirme en la sección 13).
- `ADMIN` sí puede operar sobre cualquier instructor.
- Un `PLAYER`/usuario no autenticado no puede acceder a ningún endpoint de instructor.

**4. Tests de controller (prioridad media)**
- Validación de DTOs (capacidad negativa, `playerId` faltante, etc. → `400`).
- Mapeo correcto dominio→DTO de respuesta, en particular la ocupación calculada (`3/4`).

**5. Tests de integración (prioridad media-alta, acotados)**
- Concurrencia real contra MySQL (Testcontainers, mismo patrón que
  `BookReservationConcurrencyTest`/`ReservationUniqueSlotConstraintTest`): dos inserts simultáneos
  en `court_occupancy` para el mismo `(court_id, day, start_time)` — uno debe fallar por el
  `UNIQUE`. Es el test de integración más importante de la sección 9.
- Concurrencia de `AddPlayerToClassSlotUseCase` sobre el último lugar disponible (menor prioridad
  que el anterior, dado el análisis de la sección 8 — se puede diferir si el tiempo apremia, mas no
  se recomienda saltear del todo).

No se recomienda escribir tests de integración para cada combinación de rol/endpoint (eso ya lo
cubren los tests de ownership a nivel de use case + un puñado de tests de controller con
`@WithMockUser`), siguiendo el mismo nivel de cobertura que ya tiene el resto del proyecto.

## 15. Flujo completo del MVP

```
1. ADMIN da de alta a los 4 instructores reales de la academia (RegisterInstructorUseCase).
2. Cada instructor inicia sesión (login existente, sin cambios) y define sus ClassSlot
   recurrentes (CreateClassSlotUseCase) — ej: "jueves 15:00, Cancha 2, INTERMEDIO, cupo 4".
3. Cada instructor arma el grupo fijo de cada ClassSlot (AddPlayerToClassSlotUseCase) —
   los mismos 4 alumnos que hoy coordina por WhatsApp.
4. Cada semana, el instructor crea la ClassSession de esa fecha puntual
   (CreateClassSessionUseCase) — se generan 4 ClassAttendance en PENDING automáticamente.
5. El instructor entra a "Ve sus clases" (GetClassSessionsByInstructorUseCase) — mobile,
   lista de tarjetas: "Jueves 15:00 · INTERMEDIO · Cancha 2 · 4/4".
6. Selecciona la clase (GetClassSessionDetailUseCase) — ve la lista de 4 alumnos, cada
   uno en PENDING.
7. A medida que le responden por WhatsApp (fuera del sistema, en este MVP), el instructor
   marca manualmente en el panel: Juan → CONFIRMED, María → CONFIRMED, Pedro → CANCELLED
   (ConfirmAttendanceUseCase / CancelAttendanceUseCase). Lucía queda en PENDING porque
   todavía no respondió.
8. La tarjeta de la clase recalcula la ocupación en tiempo real: 3/4 (los no-CANCELLED).
9. (Fuera de este MVP, mismo dominio sin cambios): un adapter de WhatsApp Business API
   futuro llama exactamente a ConfirmAttendanceUseCase/CancelAttendanceUseCase en
   nombre del alumno que respondió por el canal real.
```

## 16. Evolución futura

Sin rediseñar nada de lo definido acá:

- **Simulación de WhatsApp**: no es una entidad ni un use case nuevo — es, literalmente, el botón
  "Simular confirmación de WhatsApp" del frontend llamando a los mismos endpoints
  `PATCH /api/class-attendances/{id}/confirm|cancel` que ya usa el instructor manualmente. Cero
  diseño adicional requerido; se menciona explícitamente para dejar claro que no hace falta un
  "modo simulación" en el backend.
- **WhatsApp Business API real (webhook)**: un adapter de infraestructura nuevo que recibe el
  evento entrante y llama a `ConfirmAttendanceUseCase`/`CancelAttendanceUseCase` — mismos use
  cases, ninguna modificación al dominio.
- **Autoservicio de `Player`**: reutiliza `ClassAttendance.belongsTo()` (ya escrito en el dominio,
  sección 5) — solo hace falta un controller/autorización nuevos para `PLAYER`.
- **Generación automática de `ClassSession` desde `ClassSlot`** (`@Scheduled` semanal): reutiliza
  `CreateClassSessionUseCase` tal cual, disparado por un job en vez de por el instructor.
- **Lista de espera**: se agrega como tabla nueva (`class_waitlist`) que consulta
  `CancelAttendanceUseCase` al final, sin tocar los cuatro conceptos ya definidos.
- **Multi-rol** (una persona Instructor y Player a la vez): requeriría revisar el modelo actual de
  `Rol` único por `UserEntity` — no es un problema que este módulo introduzca, es una limitación
  preexistente del proyecto que quedaría más visible.

## 17. Riesgos y decisiones pendientes

Estas son las que **no** se resuelven arbitrariamente en este documento — quedan explícitas para
que el usuario decida antes de implementar:

1. **[Pendiente de confirmación] Alta de instructores exclusiva por `ADMIN`, sin autoregistro.**
   Se asumió por analogía con `Owner` (personal de la academia, no autoservicio público), pero el
   enunciado del MVP no lo dice explícitamente. Si se prefiere que un instructor se autoregistre
   como `Player` (`POST /api/players` es hoy el único endpoint de escritura público), habría que
   decidirlo antes de fijar `SecurityConfig`.
2. **[Pendiente de confirmación] 404 vs 403 en accesos ownership del módulo de clases.** Se
   recomendó seguir el patrón anti-IDOR de `Reservation` (404 siempre), pero a diferencia de una
   reserva de cancha, que un instructor "adivine" el id de la clase de otro instructor no filtra
   información tan sensible — es una decisión de producto más que de seguridad estricta, y el
   patrón `@PreAuthorize` con ownership (usado en `Court`/`Branch`/`Club`) de hecho **sí devuelve
   403** hoy, no 404 — hay una inconsistencia real y ya existente entre el patrón de
   `Reservation` (404) y el patrón de `Court`/`Branch`/`Club` (403 vía `@PreAuthorize`). Este MVP
   debe elegir uno de los dos precedentes ya existentes, no inventar un tercero — se señala la
   contradicción en vez de resolverla unilateralmente.
3. **[Riesgo de esquema] `ALTER TABLE users MODIFY COLUMN role ENUM(...)` para sumar
   `INSTRUCTOR`.** Es la única migración de este módulo que toca una tabla ya en producción con
   datos reales. Debe probarse explícitamente contra una copia de la base real antes de aplicarse
   (no alcanza con probarlo solo contra una base de test vacía) — el resto de las tablas nuevas son
   aditivas y de bajo riesgo.
4. **[Riesgo de diseño, ya documentado en la sección 9] La detección de conflicto
   Reservation↔ClassSession es por coincidencia exacta de horario de inicio, no por solapamiento
   real de intervalos.** Es una limitación heredada del propio mecanismo que ya usa el proyecto
   para reservas entre sí (`V2`), no una introducida por este diseño — pero se vuelve más visible
   ahora porque una clase y una reserva podrían tener duraciones distintas. No se resuelve acá;
   requeriría cambiar también el mecanismo existente de reservas, fuera del alcance de este MVP.
5. **[Pendiente de confirmación] Relación `ClassSession.court_id` como snapshot vs. join a través
   de `ClassSlot`.** Se propuso denormalizar (`class_session.court_id` copiado al crear) para que
   `court_occupancy` y las validaciones de conflicto no dependan de un join, siguiendo el
   precedente de `Ticket` (que también congela datos de `Court`/`Branch` al momento de emitirse).
   Es consistente con el estilo del proyecto, pero implica que si algún día se permite **mover**
   una `ClassSession` de cancha (no contemplado en este MVP), el snapshot quedaría desactualizado
   — igual que ya le pasaría a un `Ticket` si se pudiera reprogramar sin regenerarlo.
6. **[Deuda ya existente, no de este módulo] Un solo rol por usuario.** Confirmado como decisión
   definitiva para este MVP por el propio usuario — se deja registrado acá únicamente porque la
   sección 17 pide señalar todo lo que quede abierto, y esta es una restricción real del modelo
   actual (`UserEntity.role` es un único valor, no una colección) que cualquier futuro rol
   combinado (p. ej. un instructor que también juega como alumno) va a chocar contra ella.
7. **Nombres de enum (`ClassAttendanceStatus` en vez de `Status`, `ActiveStatus` reutilizado en vez
   de un `ClassSlotStatus` propio) son recomendaciones de este documento, no una re-confirmación
   explícita del usuario** — señalado en la sección 5 para que se acepten o se ajusten antes de
   escribir el código.

---

**En síntesis**: el modelo de 4 conceptos (`ClassSlot`, `ClassEnrollment`, `ClassSession`,
`ClassAttendance`) se valida tal cual fue planteado, la arquitectura hexagonal existente se
respeta sin introducir un patrón nuevo, y el único código ya existente que este MVP necesita tocar
es un efecto colateral acotado (una fila en `court_occupancy`) dentro de los use cases de
`Reservation` que ya crean/cancelan/reprograman — no su dominio ni su máquina de estados.
