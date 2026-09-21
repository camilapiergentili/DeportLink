# Análisis de diseño — Módulo de gestión de clases de pádel

> Documento de análisis puro. No se modificó ningún archivo de código para producirlo.
> Toda referencia a clases, paquetes o archivos corresponde al estado real del repositorio
> `DeportLink` al 2026-09-05 (rama de trabajo actual). Donde una decisión de diseño no está
> resuelta por el código existente, se marca explícitamente como pendiente en la sección 11.

---

# 1. Estado actual de la arquitectura

DeportLink está implementado como **arquitectura hexagonal (Ports & Adapters) + Use Cases**,
documentada así en el propio [README.md](../README.md#-arquitectura-hexagonal-ports--adapters--use-cases):

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Dominio | `domain.model` | Records inmutables con invariantes de negocio (`Reservation`, `Court`, `Schedule`, `TimeSlot`...) |
| Puertos de salida (dominio) | `domain.port.out` | Interfaces que el dominio necesita para persistir/consultar (`ReservationRepositoryPort`, `CourtRepositoryPort`, `ScheduleRepositoryPort`...) |
| Puertos de salida (aplicación) | `application.port.out` | *Gateways* de solo lectura más livianos que los use cases usan cuando solo necesitan un snapshot (`CourtGateway`, `PlayerGateway`, `ScheduleGateway`, `OwnerGateway`) |
| Casos de uso | `application.usecase.{branch,club,court,owner,player,reservation,schedule,sport}` | Una clase = una operación de negocio. Orquestan puertos y dominio, sin lógica propia |
| Adapters de infraestructura | `infrastructure.adapter` | Implementan los puertos contra JPA, con mapeo manual dominio↔entidad |
| Entidades JPA | `model.entity` | `@Entity` puras, sin lógica de negocio |
| Repositorios Spring Data | `persistence.repository` | Interfaces `JpaRepository`, incluyendo queries `@Lock(PESSIMISTIC_WRITE)` |
| Controllers | `controller` | HTTP puro: mapeo DTO↔dominio vía `mapper.dto`, `@PreAuthorize`, delegación al use case |
| Excepciones | `exception` | Jerarquía única bajo `BusinessException`, cada una con su propio `HttpStatus` |

**Puntos de diseño ya establecidos que cualquier módulo nuevo debería respetar:**

- **Dominio inmutable**: los records de dominio (`Reservation`, `Court`) no exponen setters; las
  transiciones de estado (`cancel()`, `activate()`, `markAsRescheduled()`) devuelven una instancia
  nueva y validan sus propias reglas — ver [Reservation.java](../src/main/java/com/deportlink/deportlink/domain/model/Reservation.java).
- **Doble familia de puertos out**: `domain.port.out` para el agregado completo (lectura+escritura,
  usado por el propio agregado) y `application.port.out` para *snapshots* de solo lectura que otro
  agregado necesita de forma acotada (principio ISP — ver el Javadoc de
  [CourtGateway.java](../src/main/java/com/deportlink/deportlink/application/port/out/CourtGateway.java)).
- **Excepciones abiertas/cerradas**: toda excepción de negocio nueva solo necesita extender
  `BusinessException` y declarar su `HttpStatus`; `GlobalExceptionHandler` no se toca.
- **Concurrencia con locks pesimistas como primera lectura de la transacción**: patrón repetido en
  `BookReservationUseCase`, `AddScheduleUseCase`, `CancelReservationUseCase` — el recurso disputado
  (`Court`) se bloquea con `findByIdForUpdate` *antes* de leer cualquier otro dato, para evitar
  phantom reads en escenarios de reserva concurrente del mismo slot.
- **Autorización de ownership contra la base, no contra el token**: `CourtAuthorization`,
  `BranchAuthorization`, `ClubAuthorization` — beans de Spring Security expuestos a `@PreAuthorize`
  que hacen una query directa (`existsByCourtAndOwner`, etc.).
- **Anti-IDOR en operaciones sobre reservas propias**: cancelar/reprogramar la reserva de otro
  jugador devuelve 404, no 403 (no revela existencia del recurso).

# 2. Componentes existentes reutilizables

Estos componentes son directamente aprovechables por el módulo de clases, sin modificarlos:

| Componente | Ubicación | Por qué aplica |
|---|---|---|
| `TimeSlot` (value object) | [domain/model/TimeSlot.java](../src/main/java/com/deportlink/deportlink/domain/model/TimeSlot.java) | Encapsula día+hora+duración, valida futuro y duración positiva. Una clase también ocurre en un día/hora/duración concretos. |
| Jerarquía `UserEntity` (`@Inheritance(JOINED)`) | [model/entity/UserEntity.java](../src/main/java/com/deportlink/deportlink/model/entity/UserEntity.java), `OwnerEntity`, `PlayerEntity` | Patrón ya probado para agregar un rol nuevo (`Teacher`) como subtipo de usuario con tabla propia unida por `id`. |
| Relación M:N `Owner↔Club` (`owner_club`) | [OwnerEntity.java](../src/main/java/com/deportlink/deportlink/model/entity/OwnerEntity.java), tabla `owner_club` en `real_schema.sql` | Modelo directamente análogo a "una academia puede tener múltiples profesores" (y un profesor podría trabajar en más de una academia). |
| `AddScheduleUseCase` / `ScheduleEntity` (agenda recurrente por día de semana) | [application/usecase/schedule/AddScheduleUseCase.java](../src/main/java/com/deportlink/deportlink/application/usecase/schedule/AddScheduleUseCase.java) | Ya resuelve "definir una franja recurrente por día de la semana + validar solapamientos" — el mismo problema que "todos los jueves a las 15:00". Nombre en conflicto: ver sección 11. |
| Lock pesimista sobre el recurso disputado + `findBookedSlots` | `CourtRepository.findByIdForUpdate`, `ReservationRepositoryPort.findBookedSlots` | La cancha es un recurso compartido entre reservas de cancha y (ahora) clases — la misma técnica de locking aplica para evitar que una cancha quede doblemente ocupada. |
| `BusinessException` + `GlobalExceptionHandler` | [exception/BusinessException.java](../src/main/java/com/deportlink/deportlink/exception/BusinessException.java) | Toda excepción nueva del dominio de clases (`ClassFullException`, `EnrollmentNotFoundException`, etc.) se integra sin tocar el handler. |
| `@courtAuthorization` / `@branchAuthorization` / `@clubAuthorization` (patrón) | `security/authorization/*.java` | Plantilla directa para un futuro `@classAuthorization.isTeacherOfClass(...)` o `@teacherAuthorization.belongsToAcademy(...)`. |
| `CurrentUserId` + `CurrentUserIdArgumentResolver` | `security/resolver/*.java` | Reutilizable tal cual para que un alumno confirme/cancele su propia inscripción sin pasar su id por el body. |
| `Court`, `Branch`, `Club`, `Sport` (dominio completo) | `domain/model/*.java` | Una clase de pádel ocurre en una `Court` existente, dentro de una `Branch`/`Club` existentes — no hace falta modelar de nuevo "dónde" ocurre. |
| MapStruct + patrón `mapper.dto` | `mapper/dto/*.java` | Convención ya establecida para DTOs request/response nuevos. |
| Flyway (`V1`, `V2`, `V3`) | `src/main/resources/db/migration/` | El módulo de clases se suma como `V4__...sql` en adelante, siguiendo el mismo patrón incremental. |
| Ticket / emisión de comprobante | [domain/model/Ticket.java](../src/main/java/com/deportlink/deportlink/domain/model/Ticket.java) (vía `Reservation.withTicket`) | Patrón reutilizable si en el futuro se quiere un "comprobante de inscripción a clase". |

# 3. Nuevos conceptos necesarios

Ninguno de estos existe hoy en el código y deben modelarse desde cero:

- **Profesor (Teacher)**: persona que dicta clases. No existe ningún concepto de "profesor" en
  `Rol` (`OWNER`, `PLAYER`, `ADMIN` — ver [model/Rol.java](../src/main/java/com/deportlink/deportlink/model/Rol.java)),
  ni entidad relacionada, ni tabla en `real_schema.sql`.
- **Clase (PadelClass / ClassSession)**: evento con fecha, hora, cancha, nivel y capacidad. No es
  una `Reservation` (esa representa "un jugador reserva una cancha"); una clase es "un profesor
  dicta a N alumnos en una cancha".
- **Nivel de clase (ClassLevel)**: enum nuevo `PRINCIPIANTE | INTERMEDIO | AVANZADO`. No hay ningún
  enum de nivel/skill en el proyecto actual.
- **Capacidad configurable**: hoy no existe ningún concepto de "cupo" en el dominio — una cancha
  reservada la ocupa un único jugador. Hace falta modelar la capacidad como un valor configurable
  (no una constante `4` en código), asociado a la clase (y potencialmente a un valor por defecto
  configurable a nivel academia/profesor).
- **Inscripción de alumno a clase (ClassEnrollment)**: relación N:M entre clase y alumno, con
  **estado propio** (`PENDING | CONFIRMED | CANCELLED`) — distinto del `StatusReservation` de
  reservas de cancha. No existe ningún equivalente hoy (`ReservationEntity` vincula 1 reserva a 1
  jugador, no N).
- **Horario recurrente de profesor (TeacherRecurringSchedule)**: "todos los jueves a las 15:00,
  nivel INTERMEDIO, cancha 2" — un patrón de recurrencia asociado a un *profesor*, no a una
  *cancha* como el `ScheduleEntity` actual (que define franjas de apertura de la cancha, no clases).
- **Generador de clases desde recurrencia** (a futuro): proceso que materializa instancias de
  `PadelClass` a partir de `TeacherRecurringSchedule`. No hay ningún job/scheduler en el proyecto
  hoy (no se encontró `@Scheduled` ni infraestructura de batch).
- **Canal de comunicación WhatsApp** (a futuro): puerto de salida (`NotificationGateway` o similar)
  para que el alumno confirme asistencia. Debe ser un adapter de infraestructura, nunca lógica de
  negocio — no existe hoy ningún puerto de notificación/mensajería en `application.port.out`.
- **Webhook entrante de WhatsApp Business API** (a futuro): un nuevo controller de infraestructura
  que traduce eventos externos a comandos de los use cases existentes (confirmar/cancelar
  inscripción) — nunca lógica de negocio nueva.
- **Lista de espera (Waitlist)** (a futuro): concepto de cola ordenada de alumnos por clase, para
  ofrecer automáticamente el lugar liberado por una cancelación.
- **Rol/permiso PROFESOR**: `Rol` solo tiene `OWNER`, `PLAYER`, `ADMIN` — falta decidir si un
  profesor es un usuario autenticable de DeportLink (nuevo valor de `Rol` + subtipo de
  `UserEntity`) o una entidad de negocio administrada por el Owner/Admin sin login propio (ver
  riesgo en sección 11).

# 4. Propuesta inicial del modelo de dominio

Siguiendo el estilo ya usado (`record` inmutable + método `create()` + transiciones que devuelven
nueva instancia), una primera aproximación — **a validar contra las decisiones pendientes de la
sección 11 antes de codificar**:

```java
// domain/model/ClassLevel.java
public enum ClassLevel { PRINCIPIANTE, INTERMEDIO, AVANZADO }

// domain/model/EnrollmentStatus.java
public enum EnrollmentStatus { PENDING, CONFIRMED, CANCELLED }

// domain/model/PadelClass.java
public record PadelClass(
        Long id,
        Long teacherId,
        Long courtId,
        LocalDate day,
        LocalTime startTime,
        Duration duration,
        ClassLevel level,
        int capacity,          // configurable, resuelto por el use case — no hardcodeado acá
        ClassStatus status     // p.ej. SCHEDULED | CANCELLED (a definir)
) {
    public static PadelClass create(..., int capacity) {
        if (capacity <= 0) throw new InvalidCapacityException(...);
        ...
    }
    public boolean hasAvailableSpots(int currentEnrollments) {
        return currentEnrollments < capacity;
    }
}

// domain/model/ClassEnrollment.java
public record ClassEnrollment(
        Long id,
        Long classId,
        Long studentId,        // ver sección 11: ¿Player o Student nuevo?
        EnrollmentStatus status
) {
    public ClassEnrollment confirm() { ... }
    public ClassEnrollment cancel() { ... }
}

// domain/model/TeacherRecurringSchedule.java  (nombre a definir, ver sección 11)
public record TeacherRecurringSchedule(
        Long id,
        Long teacherId,
        Long courtId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        Duration duration,
        ClassLevel level,
        int defaultCapacity,
        boolean active
) {}
```

La regla de capacidad (`capacity <= 0` inválido) se valida en el propio agregado, igual que
`TimeSlot` valida duración positiva — mismo patrón, no una constante en el use case.

# 5. Relaciones entre entidades

```
Club (Academia, si se reutiliza — ver sección 11)
└── Branch
    └── Court
        ├── Schedule (agenda de apertura de la cancha — YA EXISTE, distinto de clases)
        ├── Reservation (reserva individual de cancha — YA EXISTE)
        └── PadelClass (NUEVO)
              ├── dictada por → Teacher (NUEVO, N:1 — una clase tiene un solo profesor)
              └── ClassEnrollment (NUEVO, 1:N)
                    └── inscribe a → Player/Student (N:1)

Club (Academia)
└── Teacher (NUEVO, M:N — análogo a Club↔Owner vía owner_club)

Teacher (NUEVO)
└── TeacherRecurringSchedule (NUEVO, 1:N)
      └── genera (futuro) → PadelClass (0..N instancias materializadas)
```

Puntos de relación a resolver explícitamente (ver sección 11):

- **`Teacher` ↔ `Club`**: multiplicidad M:N (como `Owner↔Club`) si un profesor puede dar clases en
  más de una academia, o 1:N si un profesor pertenece a una sola academia. El enunciado ("una
  academia puede tener múltiples profesores") no aclara la cardinalidad inversa.
- **`PadelClass` ↔ `Court`**: N:1, igual que `Reservation`. **Ambas ocupan el mismo recurso
  compartido** (`Court`) en el mismo instante — esto es el riesgo de conflicto más importante,
  detallado en la sección 11.
- **`ClassEnrollment` ↔ alumno**: si el alumno es un `Player` existente, la relación reutiliza
  `PlayerEntity`; si es un `Student` nuevo, es una entidad paralela.
- **`TeacherRecurringSchedule` ↔ `PadelClass`**: relación de "plantilla → instancia generada", sin
  equivalente actual en el proyecto (no hay ningún patrón de "generación automática de filas" hoy).

# 6. Reglas de negocio detectadas

Extraídas literalmente del enunciado del usuario, más las que se infieren por consistencia con
reglas ya existentes en el dominio:

1. Una clase pertenece a exactamente un profesor (N:1).
2. Una clase tiene fecha, hora, cancha, nivel y capacidad — todos obligatorios.
3. El nivel es uno de un conjunto cerrado inicial (`PRINCIPIANTE`, `INTERMEDIO`, `AVANZADO`), pero
   el enunciado no descarta que crezca — **no debe hardcodearse como `String` libre ni asumirse
   que nunca se agregarán valores** (mismo criterio que ya se aplicó con `capacity`).
4. La capacidad es configurable, con valor inicial de referencia 4, **nunca una constante en
   código** — análogo a cómo `cancellationWindowHours` vive en `Branch` y no hardcodeado en
   `Reservation` (ver comentario en [Reservation.java:46-53](../src/main/java/com/deportlink/deportlink/domain/model/Reservation.java#L46)).
5. Una clase puede tener múltiples alumnos, hasta el límite de su capacidad — regla de invariante
   que, por el patrón ya usado en `BookReservationUseCase` (lock pesimista + verificación de slot
   ocupado), **debe protegerse con lock pesimista** para evitar que dos inscripciones concurrentes
   superen la capacidad (mismo riesgo de *race condition* que la doble reserva de un slot).
6. Cada alumno tiene un estado independiente por clase: `PENDING`, `CONFIRMED`, `CANCELLED` — es
   una máquina de estados por inscripción, no por clase. Sigue el mismo patrón que
   `StatusReservation`, pero es un concepto distinto y no debe confundirse ni reutilizar el enum
   existente.
7. Los profesores tienen horarios recurrentes (día de semana + hora + nivel + cancha) — de los
   cuales, en una etapa futura, el sistema generará clases automáticamente. Es decir: **la
   recurrencia es la fuente de verdad; la clase concreta es una instancia derivada** (patrón
   plantilla→instancia, sin precedente en el código actual).
8. Confirmación de asistencia por WhatsApp — el canal es solo transporte; toda regla de negocio
   (qué estado toma la inscripción, si hay cupo, etc.) vive en la aplicación, no en el canal. El
   proyecto ya seguiría este principio de forma consistente con cómo separa dominio de
   infraestructura.
9. Cancelación de un alumno → en el futuro, oferta automática a lista de espera. Hoy no hay ningún
   mecanismo de cola/waitlist en el proyecto.
10. Alumnos nuevos pueden autoregistrarse y reservar clases directamente desde la web — análogo al
    autoregistro público de `Player` (`POST /api/players`, único endpoint de escritura público
    además del login).
11. Regla implícita no mencionada pero necesaria por consistencia con `Reservation`: **una cancha
    no debería poder tener una `Reservation` y una `PadelClass` simultáneas en el mismo slot** —
    hoy son dos agregados completamente independientes que no se conocen entre sí (ver riesgo
    crítico en sección 11).

# 7. Roles y permisos

**Estado actual** ([model/Rol.java](../src/main/java/com/deportlink/deportlink/model/Rol.java)):

```java
public enum Rol { OWNER, PLAYER, ADMIN }
```

Autenticación vía JWT stateless (`UserMain implements UserDetails`, autoridad única
`ROLE_<rol>`), autorización por método con `@PreAuthorize` en cada controller, más los tres beans
de ownership (`courtAuthorization`, `branchAuthorization`, `clubAuthorization`) para verificar
pertenencia real contra la base, no solo el rol del token.

**Lo que pide el enunciado — "profesor, administrador y alumno con permisos diferentes"** — no
tiene hoy representación:

- `ADMIN` ya existe y sus permisos actuales (aprobar/rechazar clubes y sucursales, alta de Owners)
  son extensibles razonablemente a "aprobar profesores" o "supervisar clases", sin conflicto.
- `PLAYER` ya existe; si "alumno" se modela como `Player`, sus permisos actuales (autoregistro,
  reservar/cancelar/reprogramar) se extenderían con "inscribirse/cancelar inscripción a una clase".
- **`PROFESOR` no existe en ningún nivel** (ni `Rol`, ni entidad, ni controller, ni autorización).
  Decisión pendiente y estructural (ver sección 11): si es un rol de login propio, requiere:
  - agregar `PROFESOR` a `Rol`,
  - una `TeacherEntity extends UserEntity` (mismo patrón `@PrimaryKeyJoinColumn` que
    `OwnerEntity`/`PlayerEntity`),
  - un bean de ownership nuevo (`@teacherAuthorization.isOwnerOfClass(...)`) siguiendo el patrón
    de `CourtAuthorization`,
  - reglas `@PreAuthorize` específicas: un profesor gestiona (crea/edita/cancela) únicamente sus
    propias clases y ve únicamente sus propios horarios recurrentes; un alumno gestiona únicamente
    sus propias inscripciones (mismo patrón anti-IDOR que `CancelReservationUseCase` — 404 en vez
    de 403 al operar sobre la inscripción de otro alumno).
  - Alternativa: si el profesor **no** inicia sesión en DeportLink (es solo un dato administrado
    por el Owner/Admin, sin credenciales propias), no hace falta rol nuevo ni JWT — solo una
    entidad de catálogo. Esto cambia sustancialmente el alcance de seguridad del módulo.

# 8. Casos de uso propuestos

Manteniendo la convención "una clase = una operación de negocio" (`application.usecase.<dominio>`):

**`application.usecase.teacher/`** (si se decide que el profesor es una entidad gestionable, con o
sin login — ver sección 11):
- `RegisterTeacherUseCase` (análogo a `RegisterOwnerUseCase`, probablemente alta por Admin/Owner)
- `UpdateTeacherUseCase`, `DeleteTeacherUseCase`, `GetTeacherByIdUseCase`
- `AddTeacherToClubUseCase` / `RemoveTeacherFromClubUseCase` (análogo a
  `AddOwnerToClubUseCase`/`RemoveOwnerFromClubUseCase`)

**`application.usecase.teacherschedule/`** (horarios recurrentes):
- `AddTeacherRecurringScheduleUseCase`
- `UpdateTeacherRecurringScheduleUseCase`
- `DeleteTeacherRecurringScheduleUseCase`
- `GetRecurringSchedulesByTeacherUseCase`

**`application.usecase.padelclass/`**:
- `CreateClassUseCase` (manual, etapa inicial — antes de la generación automática)
- `UpdateClassUseCase`, `CancelClassUseCase`
- `GetAvailableClassesUseCase` (búsqueda pública para alumnos, análoga a `GetAvailableSlotsUseCase`)
- `GetClassesByTeacherUseCase`, `GetClassByIdUseCase`
- `GenerateClassesFromRecurringSchedulesUseCase` (etapa futura — job/batch)

**`application.usecase.enrollment/`**:
- `EnrollStudentInClassUseCase` (valida cupo con lock pesimista — mismo patrón que
  `BookReservationUseCase`)
- `ConfirmEnrollmentUseCase` (usado también por el futuro canal WhatsApp, vía el mismo use case)
- `CancelEnrollmentUseCase` (dispara, a futuro, `OfferSpotToWaitlistUseCase`)
- `GetEnrollmentsByClassUseCase`, `GetEnrollmentsByStudentUseCase`

**Controllers nuevos** (siguiendo el patrón `XController` + `XOwnerController`/`XAdminController`
ya usado por Club/Branch/Court):
- `TeacherController` (CRUD, según decisión de rol)
- `PadelClassController` (creación/gestión por profesor o admin, consulta pública)
- `EnrollmentController` (inscripción/confirmación/cancelación por el alumno)
- `TeacherRecurringScheduleController`
- (Futuro, infraestructura pura) `WhatsAppWebhookController` — traduce eventos entrantes a llamadas
  a `ConfirmEnrollmentUseCase`/`CancelEnrollmentUseCase`, sin lógica de negocio propia.

# 9. Impacto sobre la base de datos

Tablas nuevas necesarias (siguiendo el estilo de `real_schema.sql`: PK `id` autoincremental,
`@Enumerated(STRING)` para enums, índices sobre las combinaciones de consulta frecuente):

| Tabla nueva | Notas |
|---|---|
| `teachers` | Si es subtipo de `users` (login propio): `id` FK a `users.id`, igual que `owners`/`players`. Si no tiene login: tabla independiente con sus propios datos de contacto. |
| `teacher_club` | Solo si M:N — mismo patrón que `owner_club` (`teacher_id`, `club_id`). |
| `class` (o `padel_class` — evitar colisión de nombre con la tabla `court` no aplica, pero sí con el propio dominio) | `id`, `teacher_id` (FK), `court_id` (FK), `class_day`, `start_time`, `duration`, `level` (enum), `capacity` (int, NOT NULL), `status`. Índice compuesto `(court_id, class_day, start_time)` — necesario para detectar solapamiento con `reservation` (ver riesgo sección 11). |
| `class_enrollment` | `id`, `class_id` (FK), `student_id` (FK a `players` o a una tabla `students` nueva), `status` (enum `PENDING/CONFIRMED/CANCELLED`). Índice `(class_id, status)` y `(student_id)`, análogos a los índices ya existentes en `reservation`. Restricción de unicidad `(class_id, student_id)` para impedir doble inscripción del mismo alumno a la misma clase. |
| `teacher_recurring_schedule` | `id`, `teacher_id` (FK), `court_id` (FK), `day_of_week` (enum), `start_time`, `duration`, `level`, `default_capacity`, `active`. Nombre elegido para no colisionar con la tabla `availability` (`ScheduleEntity`) ya existente, que significa algo distinto (apertura de cancha, no agenda de profesor). |
| `class_waitlist` (futuro) | `id`, `class_id`, `student_id`, `position`/`created_at` para orden FIFO. |

Migraciones: continuar la numeración Flyway existente
(`V1__baseline.sql`, `V2__add_unique_reservation_slot.sql`, `V3__add_branch_cancellation_window.sql`)
con `V4__...` en adelante, una migración por cambio incremental, como ya es la convención del
proyecto.

**Impacto sobre tablas existentes**: ninguna migración destructiva es necesaria para el modelo
base — es aditivo. El único cambio potencial a una tabla existente sería agregar `PROFESOR` al
enum `role` de `users` si se decide que el profesor es un usuario con login (`ALTER TABLE users
MODIFY role ENUM(...)` — cambio no trivial en MySQL con Flyway, a planificar con cuidado).

# 10. Impacto sobre la API

Nuevos endpoints, siguiendo el prefijo `/api` y la convención `.../owner`, `.../admin` ya usada:

```
POST   /api/teachers                         (alta — Admin u Owner del club, según decisión de rol)
GET    /api/teachers/{id}
PUT    /api/teachers/{id}
DELETE /api/teachers/{id}
POST   /api/clubs/owner/{idClub}/teachers    (vincular profesor a academia — análogo a owners de club)

POST   /api/teachers/{idTeacher}/recurring-schedules
GET    /api/teachers/{idTeacher}/recurring-schedules
PUT    /api/recurring-schedules/{id}
DELETE /api/recurring-schedules/{id}

POST   /api/classes                          (crear clase — Profesor dueño o Admin)
GET    /api/classes/available                (búsqueda pública para alumnos — filtro por nivel/fecha/sede)
GET    /api/classes/{id}
PUT    /api/classes/{id}
DELETE /api/classes/{id}                     (cancelar clase)
GET    /api/classes/teacher/{idTeacher}

POST   /api/classes/{idClass}/enrollments    (alumno se inscribe)
PATCH  /api/enrollments/{id}/confirm         (alumno confirma asistencia — también target futuro del webhook WhatsApp)
DELETE /api/enrollments/{id}                 (alumno cancela — 404 anti-IDOR si no es su inscripción, igual que reservas)
GET    /api/enrollments/student/{idStudent}
GET    /api/enrollments/class/{idClass}      (profesor/admin ve inscriptos)

(futuro) POST /api/webhooks/whatsapp         (endpoint de infraestructura, sin lógica de negocio propia)
```

DTOs nuevos en `dto/request` y `dto/response` (mismo patrón que `ReservationRequestDto`,
`ScheduleRequestDto`): `TeacherRequestDto/ResponseDto`, `PadelClassRequestDto/ResponseDto`,
`EnrollmentResponseDto`, `TeacherRecurringScheduleRequestDto/ResponseDto`.

`SecurityConfig` requiere un ajuste puntual: si el alumno puede autoregistrarse **y reservar
clases directamente**, hay que decidir si `POST /api/classes/{id}/enrollments` requiere
`PLAYER` autenticado (como hoy reservar cancha) — probablemente sí, coherente con que ya
`POST /api/players` es el único registro público, y luego todo lo demás requiere JWT.

# 11. Riesgos o decisiones pendientes

Estas son las decisiones que, si no se resuelven antes de escribir código, generan el mayor riesgo
de retrabajo o de bugs de integridad:

1. **[CRÍTICO] Doble ocupación de cancha entre `Reservation` y `PadelClass`.** Hoy `Reservation` y
   el futuro `PadelClass` son agregados completamente independientes que compiten por el mismo
   recurso físico (`Court`) en el mismo slot de tiempo, sin ningún mecanismo que los haga
   conscientes uno del otro. `BookReservationUseCase` solo consulta `findBookedSlots` sobre
   `ReservationRepositoryPort` — no sabe nada de clases. Si no se resuelve, un jugador podría
   reservar la misma cancha/horario que ya tiene una clase de pádel asignada, y viceversa.
   **Debe decidirse un modelo de disponibilidad único** (¿una tabla de "ocupación de cancha"
   compartida entre ambos agregados? ¿una validación cruzada explícita en ambos use cases?) antes
   de implementar la creación de clases.

2. **¿Qué es una "academia"?** El enunciado usa "academia" y el dominio ya tiene `Club`. Si una
   academia de pádel es simplemente un `Club` (posiblemente restringido por `ClubType` o por
   deporte vía `Sport`), se reutiliza toda la jerarquía Club→Branch→Court sin cambios. Si es un
   concepto nuevo y distinto de `Club`, se duplica buena parte del modelo existente
   (aprobación, activación, sucursales) sin necesidad clara. **Recomendación implícita del
   análisis: reutilizar `Club` como academia**, salvo que el negocio tenga una razón explícita
   para separarlos.

3. **¿Quién es el "alumno"?** ¿Es el `Player` ya existente (que hoy reserva canchas) inscribiéndose
   además a clases, o una entidad `Student` nueva y separada? Esto determina si `ClassEnrollment`
   apunta a `players.id` (reutilización directa) o requiere una tabla y un flujo de registro
   propios. El enunciado dice "alumnos actuales" (sugiere que ya son usuarios/Players conocidos) y
   "alumnos nuevos podrán registrarse... y reservar clases directamente desde la web" (sugiere el
   mismo flujo de autoregistro que ya tiene `Player`). **Indicio fuerte de que "alumno" = `Player`**,
   pero es una decisión de negocio a confirmar, no una inferencia garantizada por el código.

4. **¿El profesor es un usuario del sistema (con login) o una entidad administrada por otro rol?**
   Determina si hace falta: nuevo valor de `Rol`, nueva subclase de `UserEntity`, flujo de alta con
   contraseña, JWT con `ROLE_PROFESOR`, y toda la superficie de autorización asociada — o si
   alcanza con una tabla de catálogo gestionada por `Owner`/`Admin` sin autenticación propia. Esta
   decisión cambia sustancialmente el tamaño de la etapa 1 de implementación.

5. **Colisión de nombres con `Schedule`.** `ScheduleEntity`/`Schedule` ya existen y significan
   "franja horaria de apertura de una cancha por día de semana" (tabla `availability`). El
   enunciado usa "horarios recurrentes" para el profesor, un concepto distinto. Usar el mismo
   nombre (`Schedule`) para ambos generaría ambigüedad severa en el código y en el equipo. Se
   recomienda un nombre sin colisión, p. ej. `TeacherRecurringSchedule` o `RecurringClassSlot`.

6. **Configuración de la capacidad por defecto.** El enunciado pide capacidad configurable (no
   hardcodeada), pero no dice a qué nivel vive el default: ¿es un valor global de la aplicación
   (`application.properties`), un atributo de la academia/`Club`, un atributo del profesor, o
   simplemente un campo obligatorio en cada `TeacherRecurringSchedule`/`PadelClass` sin default
   implícito? Definir esto evita que el "4 inicial" termine hardcodeado igual, solo que en otro
   lugar.

7. **Estado de la clase en sí (no de la inscripción).** El enunciado detalla el estado del alumno
   respecto de la clase (`PENDING/CONFIRMED/CANCELLED`), pero no menciona si la clase misma tiene
   estados propios (¿puede cancelarse una clase completa? ¿qué pasa con sus inscripciones activas
   si el profesor la cancela?). Es una laguna a definir antes de modelar `PadelClass.status`.

8. **Multiplicidad `Teacher`↔`Club`.** M:N (como `Owner`↔`Club`) o 1:N. Afecta directamente el
   esquema de tablas (tabla puente sí/no) y las reglas de autorización (¿un profesor gestiona
   clases en todas las academias a las que pertenece, o su token debe indicar "en cuál academia
   está operando ahora"?).

9. **Confirmación por WhatsApp sin webhook (etapa intermedia).** El enunciado plantea WhatsApp
   Business API + webhooks como algo futuro, pero "los alumnos actuales podrán confirmar asistencia
   mediante WhatsApp" se lee como una necesidad más inmediata. Sin webhook, esa confirmación debe
   entrar al sistema por algún otro medio en el corto plazo (¿un endpoint que el profesor/admin
   opera manualmente al recibir el mensaje? ¿un link único que el alumno abre?). Aclarar el alcance
   real de la primera etapa evita construir infraestructura de mensajería prematura.

10. **Lista de espera (futura) — orden y disparo.** No hay información aún sobre si el orden es
    FIFO estricto, si hay un tiempo límite para que el siguiente en la lista confirme, o si la
    oferta es automática o requiere acción del profesor. Se puede diferir sin bloquear las etapas
    iniciales, pero conviene dejarlo modelado como tabla vacía desde ya si el costo es bajo (ver
    plan de etapas, sección 12).

# 12. Recomendación de implementación por etapas

Orden pensado para desbloquear valor cuanto antes sin construir sobre decisiones no tomadas
(sección 11), y sin acoplar infraestructura futura (WhatsApp, generación automática) antes de
tener el núcleo del dominio validado:

**Etapa 0 — Cerrar decisiones de diseño** (sin código): resolver los 10 puntos de la sección 11,
como mínimo los ítems 1 a 4 (son bloqueantes estructurales: recurso compartido con `Reservation`,
qué es la academia, qué es el alumno, qué es el profesor).

**Etapa 1 — Núcleo del dominio de clases, gestión 100% manual**:
- `PadelClass`, `ClassLevel`, `ClassEnrollment`, `EnrollmentStatus` (dominio + persistencia).
- CRUD de clases por el profesor/admin (sin recurrencia todavía — cada clase se crea a mano).
- Inscripción/confirmación/cancelación de alumnos, con lock pesimista sobre la clase para proteger
  la capacidad (mismo patrón que `BookReservationUseCase`).
- Validación cruzada mínima contra `Reservation` para el mismo `Court`+slot (resolviendo el riesgo
  #1), aunque sea con la solución más simple posible al inicio.
- Rol/entidad de profesor según lo decidido en la Etapa 0, con su autorización de ownership.

**Etapa 2 — Horarios recurrentes del profesor (solo definición, sin generación automática)**:
- `TeacherRecurringSchedule` (o el nombre elegido) + CRUD.
- Todavía no genera `PadelClass` automáticamente — el profesor sigue creando clases a mano, pero ya
  puede declarar su patrón recurrente para consulta/referencia.

**Etapa 3 — Generación automática de clases desde la recurrencia**:
- Job/proceso que materializa `PadelClass` a partir de `TeacherRecurringSchedule` (batch periódico
  o generación bajo demanda al consultar disponibilidad — a definir).
- Reutiliza toda la validación de conflicto de cancha ya construida en la Etapa 1.

**Etapa 4 — Confirmación de asistencia (canal manual, sin integración externa real)**:
- Endpoint/flujo para que la confirmación llegue al sistema sin depender todavía de WhatsApp
  Business API (ver riesgo #9) — deja el puerto de notificación (`NotificationGateway`) definido
  pero con un adapter simple (o ninguno) por ahora.

**Etapa 5 — Integración saliente con WhatsApp** (notificar al alumno, sin recibir webhooks
todavía): adapter de infraestructura que implementa `NotificationGateway`.

**Etapa 6 — Webhook entrante de WhatsApp Business API**: traduce eventos entrantes a los mismos
use cases de confirmación/cancelación ya existentes desde la Etapa 1 — sin lógica de negocio nueva,
solo un nuevo adapter de entrada.

**Etapa 7 — Lista de espera automática**: `class_waitlist` + `OfferSpotToWaitlistUseCase`,
disparado desde `CancelEnrollmentUseCase`.

Cada etapa es funcional por sí sola y no bloquea a las anteriores — el negocio puede operar
manualmente los horarios recurrentes y la confirmación por WhatsApp durante todo el tiempo que
tome llegar a las etapas de automatización, igual que hoy Owner/Admin operan clubes y sucursales
sin ninguna automatización de por medio.
