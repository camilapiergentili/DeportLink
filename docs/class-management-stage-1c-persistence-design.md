# Class Management — Etapa 1C: Diseño de persistencia, adapters e integración con Reservation

> Documento de diseño puro. No se creó ninguna entidad, repository, adapter, migración, bean ni
> test de integración. Todo lo que sigue queda enumerado y explicado para implementarse después,
> con aprobación explícita previa.

> **Nota de revisión de esta versión.** Corrige, contra el código y la documentación oficial de
> MySQL/Flyway, siete puntos de la versión anterior: (1) la migración `V9` NO es una transacción
> atómica de DDL+DML — MySQL hace commit implícito en el DDL; (2) el despliegue no asume el
> comportamiento de Railway — se diseña una ventana de mantenimiento explícita; (3)
> `ClassSession` **no** persiste `courtId` — se mantiene el contrato de 1B tal cual; (4) los tests
> de migración real (Testcontainers + Flyway habilitado + `ddl-auto=validate`) se distinguen de
> los tests de mapeo (H2) y de los de wiring (contexto completo); (5) el checkpoint de arranque
> del contexto se corrige: depende de los 8 adapters, no de un subconjunto; (6)
> `BookReservationUseCase`/`RescheduleReservationUseCase` conservan `SlotNotAvailableException`,
> no adoptan `CourtSlotOccupiedException`; (7) se cierran cinco decisiones menores que habían
> quedado abiertas. Ninguna sección de abajo repite la versión anterior de estos puntos — se
> corrigieron en el cuerpo, no se agregó una nota final contradictoria.

---

## 0. Corrección puntual de 1B — resultado (histórico, de la sesión anterior)

**Archivo modificado**: [CreateClassSessionUseCaseTest.java:252](../src/test/java/com/deportlink/deportlink/usecase/classsession/CreateClassSessionUseCaseTest.java#L252)

`NEXT_THURSDAY.minusWeeks(1)` podía caer exactamente en "hoy" (si `NEXT_THURSDAY` estaba a 7 días
exactos), dejando el resultado del test a merced de si `LocalTime.now()` ya había pasado las 15:00
al momento de ejecutarse. Cambiado a `minusWeeks(2)`, que garantiza una fecha entre 7 y 13 días en
el pasado sin importar la hora de ejecución, conservando el jueves (no se tocó el dominio).

**Comandos ejecutados y resultados reales, en la sesión anterior** (no se declaró la suite
completa verde — solo se corrió el módulo de clases; esta corrección de documento, en la sesión
actual, no ejecutó nada de nuevo):

```
./mvnw -q -o test -Dtest='CreateClassSessionUseCaseTest#execute_fechaPasada_propagaInvalidTimeRangeDelDominio'
→ Tests run: 1, Failures: 0, Errors: 0

./mvnw -q -o test -Dtest='ClassSlotDomainTest,ClassEnrollmentDomainTest,ClassSessionDomainTest,ClassAttendanceDomainTest,CreateClassSlotUseCaseTest,PauseClassSlotUseCaseTest,ReactivateClassSlotUseCaseTest,AddPlayerToClassSlotUseCaseTest,RemovePlayerFromClassSlotUseCaseTest,GetClassSlotsByInstructorUseCaseTest,CreateClassSessionUseCaseTest,GetClassSessionsByInstructorUseCaseTest,GetClassSessionDetailUseCaseTest,ConfirmAttendanceUseCaseTest,CancelAttendanceUseCaseTest'
→ 4 clases de dominio (1A): 55 tests, 0 failures, 0 errors
→ 11 clases de use cases (1B): 74 tests, 0 failures, 0 errors
→ Total módulo de clases: 129 tests, 0 failures, 0 errors
```

El baseline conocido de la suite completa del proyecto, con evidencia real (de la sesión de 1B):
362 tests, 0 failures, 30 errors — 6 preexistentes por falta de Docker y 24 por falta de beans
para los puertos nuevos sin adapter. Esa es la razón de ser de esta Etapa 1C.

---

## 1. Objetivo y alcance de este documento

Diseñar, sin implementar, cómo conectar los 11 casos de uso de la Etapa 1B a persistencia real
(MySQL vía JPA/Hibernate, mismo stack que el resto del proyecto), de forma que:

1. El `ApplicationContext` de Spring vuelva a arrancar (hoy no arranca — ver evidencia arriba).
2. La ocupación de cancha sea real y bidireccional entre `Reservation` y `ClassSession`.
3. Ningún flujo de borrado existente (Player/Court/Branch/Club) pueda destruir historial de
   clases ni dejar filas de `court_occupancy` huérfanas.

No se crea nada de código todavía — se lista, se explica y se ordena.

---

## 2. Estado verificado antes de diseñar (no supuesto)

Se inspeccionó el código real para basar este diseño en convenciones existentes, no inventadas:

| Verificado | Resultado |
|---|---|
| `application.properties` (default) | `spring.jpa.hibernate.ddl-auto=validate`; Flyway manda. |
| `application-dev.properties` | `ddl-auto=update`, perfil opt-in, ya documentado como solo-desarrollo. |
| `src/test/resources/application-test.properties` (perfil `test`) | H2 en memoria, **Flyway deshabilitado**, `ddl-auto=create-drop` — Hibernate genera el schema desde las anotaciones, ignorando las migraciones SQL. Esto es clave: los tests `@SpringBootTest @ActiveProfiles("test")` (`CascadeDeletionRegressionTest`, `ClubServiceTest`, etc.) validan que las **entidades JPA** sean correctas, no que las migraciones lo sean — de ahí que en la sección 9 se distinga un tercer nivel de test que sí ejecuta las migraciones reales. |
| Migraciones existentes | `V1__baseline.sql`, `V2__add_unique_reservation_slot.sql`, `V3__add_branch_cancellation_window.sql`. Estilo: nombres de constraint explícitos (`fk_owners_users`, `uq_reservation_active_slot`), comentario de cabecera explicando el porqué, backfill explícito de datos cuando aplica. **Próxima versión libre: `V4`.** |
| Conflicto Reservation↔ClassSession hoy | `BookReservationUseCase`/`CancelReservationUseCase`/`RescheduleReservationUseCase` **no fueron tocados** en 1B — no conocen `CourtOccupancyPort`. No hay ningún adapter de `CourtOccupancyPort` — la garantía hoy no existe en ningún sentido. |
| Concurrencia real | El patrón de test contra MySQL real ya existe: `BookReservationConcurrencyTest`, `RescheduleReservationConcurrencyTest`, `ReservationUniqueSlotConstraintTest`, `AddScheduleConcurrencyTest` — todos `@Testcontainers @SpringBootTest` con `MySQLContainer` + `@DynamicPropertySource`, pero **deshabilitando Flyway** y usando `ddl-auto=create-drop` contra el contenedor (Hibernate genera el schema desde las entidades, no desde las migraciones). Es un molde parcialmente reutilizable para 1C — ver sección 9.3 sobre por qué, para *este* módulo, hace falta además un nivel de test que sí ejecute Flyway. |
| Borrado de Player | `PlayerRepositoryAdapter.delete()` **anonimiza**, no borra la fila — `Reservation.player_id` nunca queda huérfano ([PlayerRepositoryAdapter.java:62-85](../src/main/java/com/deportlink/deportlink/infrastructure/adapter/PlayerRepositoryAdapter.java#L62)). |
| Borrado de Court/Branch | `DeleteCourtUseCase`/`DeleteBranchUseCase` bloquean si `hasReservations()` — implementado como `reservationRepository.existsByCourt_Id(courtId)` / `existsByCourt_Branch_Id(branchId)` ([CourtRepositoryAdapter.java:55](../src/main/java/com/deportlink/deportlink/infrastructure/adapter/CourtRepositoryAdapter.java#L55), [BranchRepositoryAdapter.java:67](../src/main/java/com/deportlink/deportlink/infrastructure/adapter/BranchRepositoryAdapter.java#L67)). Si no hay reservas, `Branch`→`Court` se borra en cascada JPA (`CascadeType.ALL, orphanRemoval=true`), verificado por `deleteBranch_sinReservas_funciona`. |
| Borrado de Club | `DeleteClubUseCase` bloquea si `hasBranches()` — un club solo se borra sin sucursales, así que nunca llega a haber `Court`/`ClassSlot` en juego a ese nivel. |
| `CourtEntity.reservations` | `@OneToMany(mappedBy = "court")` **sin ningún `cascade` declarado** — precedente exacto a seguir para `classSlots` (sección 7.5). |
| `Clock` | No existe ningún bean `Clock` en todo `src/main` — confirma por qué `GetClassSessionsByInstructorUseCase` no puede instanciarse hoy. |
| Zona horaria | No hay ninguna configuración de timezone en `application*.properties`, `compose.yaml` ni Dockerfile — toda fecha/hora del proyecto (`Reservation.create()`, `TimeSlot.isFuture()`, y el `ClassSession.create()` de 1A) depende implícitamente de la zona por defecto de la JVM donde corre el proceso. Es una ambigüedad **preexistente**, no introducida por este módulo. |
| `GlobalExceptionHandler` | `handleDataIntegrityViolation` ya traduce cualquier `DataIntegrityViolationException` a `409 Conflict` con mensaje genérico — relevante para la sección 6.7. |

---

## 3. Modelo persistente y migraciones

### 3.1 Instructor — herencia real de `UserEntity`

`UserEntity` usa `@Inheritance(strategy = InheritanceType.JOINED)` con `OwnerEntity`/`PlayerEntity`
como subtipos vía `@PrimaryKeyJoinColumn(name = "id")`, sin columnas propias en el caso de
`PlayerEntity`. `InstructorEntity` sigue exactamente ese molde:

```java
@Entity
@Table(name = "instructors")
@PrimaryKeyJoinColumn(name = "id")
public class InstructorEntity extends UserEntity {
    // Sin columnas propias — igual que PlayerEntity.
}
```

`Rol` (`model/Rol.java`) necesita el cuarto valor `INSTRUCTOR`. Es un cambio de una línea en el
enum Java, pero en la base implica:

```sql
ALTER TABLE users MODIFY COLUMN role ENUM('ADMIN','OWNER','PLAYER','INSTRUCTOR');
```

Es la única migración de este documento que **modifica una columna con datos reales en
producción** — probar contra un dump real antes de aplicar (criterio de aceptación, sección 10.6).

### 3.2 ClassSlot

```sql
CREATE TABLE IF NOT EXISTS class_slot (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    instructor_id BIGINT       NOT NULL,
    court_id      BIGINT       NOT NULL,
    day_of_week   ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    start_time    TIME(6)      NOT NULL,
    duration      BIGINT       NOT NULL,  -- minutos, vía DurationConverter (autoApply=true, ya existente)
    level         ENUM('PRINCIPIANTE','INTERMEDIO','AVANZADO') NOT NULL,
    capacity      INT          NOT NULL,
    active_status ENUM('ACTIVE','INACTIVE') NOT NULL,  -- reutiliza ActiveStatus, decisión ya cerrada
    PRIMARY KEY (id),
    KEY idx_class_slot_instructor (instructor_id),
    KEY idx_class_slot_court_day (court_id, day_of_week),
    CONSTRAINT fk_class_slot_instructor FOREIGN KEY (instructor_id) REFERENCES instructors (id),
    CONSTRAINT fk_class_slot_court FOREIGN KEY (court_id) REFERENCES court (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`day_of_week` como `ENUM` de los 7 valores de `java.time.DayOfWeek` — mismo tipo que ya usa
`availability.day_of_week` (`ScheduleEntity`), verificado contra `real_schema.sql`. `duration`
como `BIGINT` reutilizando `DurationConverter` tal cual.

No hay ninguna columna `UNIQUE` obligatoria más allá de la PK — dos instructores podrían, en
teoría, definir el mismo día/hora/cancha (sería un conflicto real detectado recién al crear
`ClassSession`, vía `CourtOccupancyPort`, no al definir el `ClassSlot`).

### 3.3 ClassEnrollment

```sql
CREATE TABLE IF NOT EXISTS class_enrollment (
    id            BIGINT  NOT NULL AUTO_INCREMENT,
    class_slot_id BIGINT  NOT NULL,
    player_id     BIGINT  NOT NULL,
    active        BIT(1)  NOT NULL,  -- mismo tipo que address.is_default (BIT(1), no BOOLEAN)
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_enrollment_slot_player (class_slot_id, player_id),
    KEY idx_class_enrollment_player (player_id),
    CONSTRAINT fk_class_enrollment_slot FOREIGN KEY (class_slot_id) REFERENCES class_slot (id),
    CONSTRAINT fk_class_enrollment_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

El `UNIQUE (class_slot_id, player_id)` **sin filtrar por `active`** — pedido explícito: un alumno
solo puede tener UNA fila por horario, esté activa o no. El dominio de
`AddPlayerToClassSlotUseCase` (1B) ya está escrito para nunca insertar una segunda fila — siempre
resuelve `findByClassSlotIdAndPlayerId` primero y hace `activate()` sobre la existente si la hay.
El adapter (`ClassEnrollmentRepositoryAdapter.save`) debe replicar el patrón de
`ReservationRepositoryAdapter.save`: `id == null` → `INSERT`, `id != null` → `UPDATE` por id — así
el `UNIQUE` nunca se viola desde el flujo normal; queda como defensa en profundidad.

### 3.4 ClassSession — se mantiene el contrato de 1B, sin `courtId`

**Decisión cerrada para esta etapa, sin excepción**: `ClassSessionRepositoryPort.save(ClassSession)`
conserva exactamente su firma de 1B. No se agrega `courtId` al record de dominio. No se agrega una
columna `court_id` a `class_session`. La cancha de una sesión se resuelve, siempre, a través de su
`ClassSlot` (`slot.courtId()`) — tal como ya lo hacía el propio `CreateClassSessionUseCase` de 1B
antes de llegar a este documento.

```sql
CREATE TABLE IF NOT EXISTS class_session (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    class_slot_id BIGINT   NOT NULL,
    session_date  DATE     NOT NULL,
    start_time    TIME(6)  NOT NULL,
    duration      BIGINT   NOT NULL,
    status        ENUM('SCHEDULED','CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_session_slot_date (class_slot_id, session_date),
    CONSTRAINT fk_class_session_slot FOREIGN KEY (class_slot_id) REFERENCES class_slot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Sin `court_id`, sin `idx_class_session_court_date`, sin `fk_class_session_court` — no hacen falta:

- **La disponibilidad compartida de cancha se consulta contra `court_occupancy` (sección 3.6),
  que ya tiene su propio `court_id`** — esa es la tabla diseñada específicamente para responder
  "¿está esta cancha ocupada en este día/hora?", y recibe el `court_id` como parámetro explícito
  en `CourtOccupancyPort.registerForClassSession(classSessionId, courtId, day, startTime)` (ya
  definido así en 1B, sin cambios). No hace falta que `class_session` sepa nada de canchas para
  que esa consulta funcione — sería duplicar un dato que ya vive, completo y consultable, en otra
  tabla pensada exactamente para eso.
- **El panel del instructor** (`GetClassSessionsByInstructorUseCase`/`GetClassSessionDetailUseCase`,
  1B) ya resuelve cancha/nivel/capacidad leyendo el `ClassSlot` de cada sesión (`classSlotRepository
  .findAllByIds`/`findById`) — ninguno de los dos use cases necesita ni pide `ClassSession.courtId`
  en su diseño de 1B.

**Snapshot que sí se mantiene, sin cambios respecto de 1A**: `session_date`/`start_time`/`duration`
siguen copiándose del `ClassSlot` al momento de crear la sesión (para que editar un `ClassSlot` —
hoy fuera de alcance de todos modos — no altere sesiones ya creadas). Ese razonamiento nunca
incluyó a `courtId`; fue un error de una versión anterior de este documento extenderlo a la cancha
sin necesidad real, y queda corregido acá.

El `UNIQUE (class_slot_id, session_date)` impide duplicar la sesión de una fecha — coincide con la
regla de dominio ya implementada en `CreateClassSessionUseCase` (`existsByClassSlotIdAndDay`).

### 3.5 ClassAttendance

```sql
CREATE TABLE IF NOT EXISTS class_attendance (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    class_session_id  BIGINT NOT NULL,
    player_id         BIGINT NOT NULL,
    status            ENUM('PENDING','CONFIRMED','CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_attendance_session_player (class_session_id, player_id),
    KEY idx_class_attendance_player (player_id),
    CONSTRAINT fk_class_attendance_session FOREIGN KEY (class_session_id) REFERENCES class_session (id),
    CONSTRAINT fk_class_attendance_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Sin sorpresas — mapea 1:1 con el record de dominio, mismo criterio de `UNIQUE` que Enrollment.

### 3.6 CourtOccupancy

```sql
CREATE TABLE IF NOT EXISTS court_occupancy (
    id            BIGINT  NOT NULL AUTO_INCREMENT,
    court_id      BIGINT  NOT NULL,
    occupied_day  DATE    NOT NULL,
    start_time    TIME(6) NOT NULL,
    source_type   ENUM('RESERVATION','CLASS_SESSION') NOT NULL,
    source_id     BIGINT  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_court_occupancy_slot (court_id, occupied_day, start_time),
    KEY idx_court_occupancy_source (source_type, source_id),
    CONSTRAINT fk_court_occupancy_court FOREIGN KEY (court_id) REFERENCES court (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`idx_court_occupancy_source` hace falta para que `releaseForReservation(reservationId)` (sección
6 — es el único método de liberación en alcance, ver 6.0) borre por origen en O(1).

### 3.7 Integridad referencial y borrado — ningún `CASCADE` nuevo

Ninguna FK de esta sección usa `ON DELETE CASCADE`. Se mantiene, extendida, la misma filosofía ya
documentada en el proyecto para `Reservation`/`Ticket`: nada de este módulo debe desaparecer como
efecto colateral de borrar otra cosa. El único `CASCADE` real del sistema (`Branch`→`Court` a nivel
JPA `orphanRemoval`) ya está protegido hoy por `hasReservations()`; la sección 5 explica por qué
eso ya no alcanza una vez que existe `ClassSlot`.

### 3.8 Migraciones propuestas (a partir de V4)

Una por cambio incremental, seis en total, mismo criterio que V1–V3:

| Versión | Contenido |
|---|---|
| `V4__add_instructor_role_and_table.sql` | `ALTER TABLE users MODIFY role ENUM(...,'INSTRUCTOR')` + `CREATE TABLE instructors`. |
| `V5__add_class_slot.sql` | Tabla `class_slot`. |
| `V6__add_class_enrollment.sql` | Tabla `class_enrollment`. |
| `V7__add_class_session.sql` | Tabla `class_session` (sin `court_id`, sección 3.4). |
| `V8__add_class_attendance.sql` | Tabla `class_attendance`. |
| `V9__add_court_occupancy.sql` | Tabla `court_occupancy` + backfill de `Reservation` activas. **No es una única transacción atómica de DDL+DML** — ver sección 8.3, con cita de la documentación oficial de MySQL. |

No se cambia `ddl-auto` en ningún perfil, ni se edita ninguna migración ya aplicada.

---

## 4. Adapters y wiring — puerto por puerto

| Puerto (1B) | Adapter propuesto | Query(s) | Mapeo dominio↔entidad | Lock / transacción | Batch |
|---|---|---|---|---|---|
| `ClassSlotRepositoryPort` | `ClassSlotRepositoryAdapter` (+ `ClassSlotRepository extends JpaRepository`) | `findById`, `@Lock(PESSIMISTIC_WRITE) findByIdForUpdate`, `findAllByInstructor_Id`, `findAllByIdIn` | Campo a campo, sin MapStruct (mismo criterio que `ScheduleRepositoryAdapter`). `save`: `id==null`→INSERT, si no→carga+actualiza (mismo patrón `ReservationRepositoryAdapter`). | El lock lo toma el use case llamando a `findByIdForUpdate`. | `findAllByIdIn(Set<Long>)` para `GetClassSessionsByInstructorUseCase`. |
| `ClassEnrollmentRepositoryPort` | `ClassEnrollmentRepositoryAdapter` | `findByClassSlot_IdAndPlayer_Id`, `findByClassSlot_IdAndActiveTrue`, `countByClassSlot_IdAndActiveTrue` | Igual criterio manual. | Sin lock propio — protegido por el lock de `ClassSlot` que ya toma `AddPlayerToClassSlotUseCase`. | — |
| `ClassSessionRepositoryPort` | `ClassSessionRepositoryAdapter` | `findById`, `existsByClassSlot_IdAndSessionDate`, `findByClassSlot_Instructor_IdAndSessionDateGreaterThanEqual` (variante `...LessThanEqual` cuando `to != null`; se recomienda `@Query` explícita en vez de dos derived queries condicionales) | 1:1 con el record — sin `courtId` en ningún lado (sección 3.4, contrato de 1B sin cambios). | Ninguno propio — el lock relevante es el de `ClassSlot`/`Court`, tomado antes en `CreateClassSessionUseCase`. | — |
| `ClassAttendanceRepositoryPort` | `ClassAttendanceRepositoryAdapter` | `findById`, `findByClassSession_Id`, `saveAll` (heredado), `countByStatusForSessions` vía una `@Query` de agregación (`GROUP BY` sobre `class_session_id, status`) | Igual criterio manual. | Sin lock — confirmar/cancelar asistencia no compite por ningún recurso compartido. | La agregación `GROUP BY` es en sí misma la consulta batch — una sola ida a la base para N sesiones. |
| `InstructorGateway` | `InstructorGatewayAdapter` (+ `InstructorRepository extends JpaRepository<InstructorEntity, Long>`, sin métodos propios) | `findById` heredado | `new InstructorSnapshot(entity.getId())` | — | — |
| `ClassSlotCourtGateway` | `ClassSlotCourtGatewayAdapter` | `@Lock(PESSIMISTIC_WRITE) @Query("SELECT c FROM CourtEntity c JOIN c.classSlots s WHERE s.id = :classSlotId")`, devolviendo `court.getId()` | — | Ver sección 7.2/7.5 — requiere `Set<ClassSlotEntity> classSlots` en `CourtEntity`, sin cascade (cerrado, sección 7.5). Qué filas quedan bajo lock en el SQL real generado se confirma al implementar, no se afirma acá (sección 7.2). | — |
| `PlayerRosterGateway` | `PlayerRosterGatewayAdapter` | `PlayerRepository.findAllById(Set<Long>)` (heredado, acepta `Iterable<ID>`) | `Map` armado en el adapter | — | Ya es una sola consulta — el propósito por el que este gateway se separó de `PlayerGateway` en 1B. |
| `CourtOccupancyPort` | `CourtOccupancyAdapter` (+ `CourtOccupancyRepository`) | `existsByCourt_IdAndOccupiedDayAndStartTime`; `save`; `deleteBySourceTypeAndSourceId` (usado solo con `source_type='RESERVATION'` en 1C, ver 6.0) | Entidad simple, sin dominio propio. | No toma lock propio — se apoya en que su caller ya tiene a Court bloqueada (ver 7.3 sobre lectura consistente vs. bloqueante). | — |

### 4.1 Bean `Clock`

```java
package com.deportlink.deportlink.config;

@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
```

**Decisión cerrada**: paquete nuevo `config/`, separado de `security/config/` y de `WebConfig` —
`Clock` no es un concepto de seguridad ni de configuración web, y forzarlo en cualquiera de esos
dos sería más confuso que un paquete propio de una sola clase.

`Clock.systemDefaultZone()` usa la misma zona que `LocalDateTime.now()`/`LocalDate.now()` ya usan
implícitamente en todo el resto del proyecto (`Reservation.create()`, `TimeSlot.isFuture()`, y el
propio `ClassSession.create()` de 1A) — no introduce ninguna discrepancia nueva. La ambigüedad de
fondo (ninguna zona horaria está fijada explícitamente en ningún lado del proyecto) es preexistente
a este módulo y no se resuelve acá.

### 4.2 Instructor mínimo — deslinde explícito

Lo mínimo para que `class_slot.instructor_id` tenga a qué apuntar y `InstructorGateway` resuelva
algo real:

- `Rol.INSTRUCTOR` (enum Java) + migración `V4` (sección 3.1).
- `InstructorEntity` (sección 3.1) + `InstructorRepository` + `InstructorGatewayAdapter`.

Lo que sigue diferido, sin tocar en 1C:

- Alta con credenciales (`RegisterInstructorUseCase`) — para los tests de integración de 1C, la
  fila de `instructors` se inserta directamente por JPA (mismo patrón que
  `CascadeDeletionRegressionTest.setUp()` ya hace con `OwnerEntity`/`PlayerEntity`).
- Cualquier endpoint HTTP, DTO, o `@PreAuthorize`.
- **JWT/`SecurityConfig`: no hace falta tocarlos.** Verificado: `UserDetailsServiceImpl` carga
  `UserEntity` por email sin conocer subtipos, y `UserMain.getAuthorities()` deriva la autoridad de
  `user.getRole().name()` — agregar `INSTRUCTOR` a `Rol` y una fila en `instructors` no requiere
  ningún cambio en `JwtFilter`, `JwtUtil`, `AuthController`, `AuthService` ni `SecurityConfig`.

---

## 5. Cambios acotados necesarios en flujos existentes (no implementados aún)

Verificado contra `DeleteCourtUseCase`/`DeleteBranchUseCase`/`DeleteClubUseCase`/`DeletePlayerUseCase`
reales (sección 2) — necesario para que no permitan borrar historial de clases ni dejar
ocupaciones huérfanas:

| Flujo | Problema si no se cambia | Cambio propuesto |
|---|---|---|
| `DeleteCourtUseCase` | Hoy solo verifica `hasReservations(courtId)`. Si una `Court` tiene un `ClassSlot` pero cero `Reservation`, el chequeo actual pasaría y el `DELETE` chocaría contra `fk_class_slot_court` (RESTRICT) — la app no perdería datos, pero el error sería un `DataIntegrityViolationException` genérico (409), no un mensaje de negocio claro. | Agregar `CourtRepositoryPort.hasClassSlots(Long courtId)` (`classSlotRepository.existsByCourt_Id(courtId)`); chequear junto a `hasReservations`; lanzar `CourtHasClassSlotsException` (409, mismo estilo que `CourtHasReservationsException`). |
| `DeleteBranchUseCase` | Mismo problema, a través de todas las canchas de la sucursal. | Agregar `BranchRepositoryPort.hasClassSlots(Long branchId)` (`existsByCourt_Branch_Id`); chequear junto a `hasReservations`; lanzar `BranchHasClassSlotsException` (409). |
| `DeleteClubUseCase` | Ninguno — ya bloquea si `hasBranches()`. | Sin cambios. |
| `DeletePlayerUseCase` | Ninguno — anonimiza en vez de borrar. | Sin cambios. |

**Excepciones nuevas**: `CourtHasClassSlotsException`, `BranchHasClassSlotsException` (409, mismo
criterio que las existentes).

**Archivos existentes a modificar** (también en sección 10): `CourtRepositoryPort`,
`BranchRepositoryPort`, `CourtRepositoryAdapter`, `BranchRepositoryAdapter`, `DeleteCourtUseCase`,
`DeleteBranchUseCase`. Son chequeos adicionales — ninguno relaja un comportamiento existente.

---

## 6. Ocupación compartida con Reservation — integración completa

`CourtOccupancyPort` (1B) define `existsOccupancy`/`registerForClassSession`. Para integrar con
`Reservation` hace falta ampliarlo (sin adapter todavía — extenderlo ahora no rompe nada
construido):

```java
public interface CourtOccupancyPort {
    boolean existsOccupancy(Long courtId, LocalDate day, LocalTime startTime);
    void registerForClassSession(Long classSessionId, Long courtId, LocalDate day, LocalTime startTime);
    void registerForReservation(Long reservationId, Long courtId, LocalDate day, LocalTime startTime); // NUEVO
    void releaseForReservation(Long reservationId); // NUEVO — borra por origen, nunca por coordenadas
}
```

**`releaseForClassSession` NO se agrega** — cancelar una `ClassSession` completa sigue fuera de
alcance del MVP (no hay ningún caso de uso que la dispare); agregar un método sin ningún caller
sería especular sobre una etapa futura que este documento no está diseñando.

**Por qué "liberar" es por origen (`source_type` + `source_id`) y no por `(court, día, hora)`**:
identificar por origen hace que la operación sea correcta con independencia de que el lock de
Court efectivamente evite una carrera — `idx_court_occupancy_source` es lo que hace esa consulta
barata.

### 6.1 `BookReservationUseCase`

Secuencia actual (sin tocar): lock Court → buscar Player → buscar `SlotConfig` → validar slot →
`findBookedSlots` (conflicto contra otras `Reservation`) → crear `Reservation` de dominio →
calcular precio/`Ticket` → `save`.

**Inserción propuesta**, justo después de `findBookedSlots`:

```java
if (courtOccupancyPort.existsOccupancy(courtId, day, startTime)) {
    throw new SlotNotAvailableException("El horario ya está ocupado");
}
```

**Se reutiliza `SlotNotAvailableException`** — la misma excepción que ya lanza este use case
cuando el conflicto es contra otra `Reservation` (`"El horario ya está reservado"`) — con un
mensaje distinto pero genérico, **sin decir "por una clase"**: `existsOccupancy` devuelve un
booleano, no identifica el origen de la ocupación, y `BookReservationUseCase` no tiene por qué
saberlo ni comunicarlo. Esto preserva el contrato de excepciones que ya conocen los callers de
`Reservation` — no se introduce un tipo nuevo en este flujo.

Y **después** de `reservationRepository.save(...)` (ya con el id real asignado):

```java
courtOccupancyPort.registerForReservation(saved.id(), courtId, day, startTime);
```

Todo dentro del mismo `@Transactional` existente.

### 6.2 `CancelReservationUseCase`

Secuencia actual: buscar Player → buscar `Reservation` → validar pertenencia → buscar `Court` →
`reservation.cancel(...)` → `save`.

**Inserción propuesta**, después de que `save(cancelled)` tuvo éxito:

```java
courtOccupancyPort.releaseForReservation(reservationId);
```

Si `cancel()` lanza, el release ni se intenta.

### 6.3 `RescheduleReservationUseCase`

Secuencia actual: lock Court (derivado por join) → buscar Player → buscar `Reservation` → validar
pertenencia → re-lock (re-entrante) → buscar `SlotConfig` del nuevo día → validar nuevo slot →
`findBookedSlots` del nuevo slot → `markAsRescheduled()` + `save` → crear nueva `Reservation` +
`Ticket` → `save`.

**Regla de orden para no perder la ocupación anterior si algo falla**: el chequeo de conflicto
contra `CourtOccupancyPort` para el nuevo slot debe ocurrir antes de tocar cualquier fila
existente — mismo punto que la validación de `findBookedSlots` del nuevo slot:

```java
if (courtOccupancyPort.existsOccupancy(courtId, newDay, newStartTime)) {
    throw new SlotNotAvailableException("El horario ya está ocupado");
}
```

Mismo criterio que 6.1: `SlotNotAvailableException`, mensaje genérico, sin mencionar clases.

Recién si esa validación pasa: `markAsRescheduled()` + `save` (reserva vieja) → crear + `save`
(reserva nueva) → `releaseForReservation(oldReservationId)` → `registerForReservation(newReservationId, ...)`.

**Por qué esto alcanza sin lógica de compensación manual**: las cuatro operaciones viven en el
mismo `@Transactional` que ya envuelve el método. Si la validación de conflicto se hace antes de
mutar nada, un fallo ahí no dejó ningún efecto que revertir. Si algo fallara después, Spring hace
rollback de la transacción completa: la reserva vieja vuelve a `RESERVADO`, la nueva desaparece, y
el `DELETE` de la ocupación vieja también se revierte — se conserva sola, por atomicidad de la
transacción, sin código de "restaurar" escrito a mano. (Esta atomicidad es la de una transacción
InnoDB normal de solo DML — no tiene relación con la limitación de DDL de la sección 8.3, que es
específica de sentencias `CREATE TABLE`/`ALTER TABLE` en las migraciones, no de este código.)

### 6.4 `CreateClassSessionUseCase` (1B) — sin cambios de fondo

Ya define el mismo patrón (`existsOccupancy` antes de guardar, `registerForClassSession` después,
con el id real de la sesión) y **mantiene `CourtSlotOccupiedException`** — es el único lugar del
sistema donde esa excepción se usa; no se propaga a `Reservation` (sección 6.1/6.3). El único
cambio real de 1C acá es que ahora existirá un adapter detrás de `CourtOccupancyPort` — el use
case no cambia una línea.

### 6.5 Reversión si falla guardar la sesión o las asistencias

Si `classSessionRepository.save(session)` lanza, el método aborta antes de
`registerForClassSession`/`saveAll` de asistencias. Si `classAttendanceRepository.saveAll(...)`
fallara (último paso), el rollback deshace también el `save` de la sesión y el
`registerForClassSession` ya ejecutado.

### 6.6 Limitación que se mantiene explícita

Coincidencia **exacta** de `(court_id, día, start_time)`, no solapamiento de intervalos de
duración distinta — misma limitación ya aceptada por `uq_reservation_active_slot` (V2) para
reservas entre sí. No se resuelve en esta etapa ("no rediseñar Reservation").

**Implementar solo el adapter de `CourtOccupancyPort` no alcanza** — sin los cambios de 6.1–6.3 en
`Reservation`, la protección sigue siendo unidireccional.

### 6.7 Violación del `UNIQUE` en el flush/commit — qué pasa realmente

Aunque el lock de Court (sección 7) está pensado para que la carrera no ocurra, vale documentar
qué pasaría si igualmente sucediera: Hibernate no necesariamente ejecuta el `INSERT` físico en el
momento en que se llama a `save()` — con el modo de flush por defecto de JPA, la sentencia puede
diferirse hasta el flush de la transacción, que en un método `@Transactional` simple suele
coincidir con el commit, al final del método. Una violación del `UNIQUE uq_court_occupancy_slot`
en ese escenario **puede no manifestarse como una excepción capturable dentro de un `try/catch`
puesto alrededor de la llamada a `registerForReservation`/`registerForClassSession`** — surge
recién cuando Spring intenta comitear, en el borde del proxy `@Transactional`, no en medio del
cuerpo del método.

Lo que sí es cierto y ya está verificado en el proyecto: esa excepción
(`DataIntegrityViolationException`) ya tiene manejador genérico en
`GlobalExceptionHandler.handleDataIntegrityViolation` → `409 Conflict`, mensaje "No se pudo
completar la operación porque el recurso tiene datos asociados". La transacción hace rollback
correctamente en cualquier caso (lo garantiza el proxy transaccional de Spring, sin importar en
qué momento exacto Hibernate haya emitido el `INSERT`) — lo que no se promete acá es un mensaje de
negocio más específico para este caso puntual sin decidir, al implementar, si conviene forzar un
flush explícito dentro del propio método para poder capturarlo ahí y traducirlo — decisión de
implementación, no de este documento.

---

## 7. Locks y transacciones

### 7.1 Orden por operación

| Operación | Locks, en orden |
|---|---|
| `CreateClassSessionUseCase` | Court (vía `ClassSlotCourtGateway`) → ClassSlot — confirmado, `findCourtIdByClassSlotForUpdate` es la primera línea del método (1B). |
| `PauseClassSlotUseCase` / `ReactivateClassSlotUseCase` / `AddPlayerToClassSlotUseCase` / `RemovePlayerFromClassSlotUseCase` | Solo ClassSlot — `findByIdForUpdate(classSlotId)` es la primera línea en los cuatro (1B). |
| `BookReservationUseCase` | Solo Court — ya existente, sin cambios. |
| `RescheduleReservationUseCase` | Solo Court (derivado por join) — ya existente, sin cambios. |
| `CancelReservationUseCase` | Ninguno (ya documentado en 1B). |

### 7.2 Cómo `ClassSlotCourtGateway` deriva y bloquea Court sin leer antes el ClassSlot

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM CourtEntity c JOIN c.classSlots s WHERE s.id = :classSlotId")
Optional<CourtEntity> findByClassSlotIdForUpdate(@Param("classSlotId") Long classSlotId);
```

Mismo texto de consulta que `CourtRepository.findByReservationIdForUpdate` (`JOIN c.reservations r
WHERE r.id = :reservationId`), que sí tiene un test de concurrencia real
(`RescheduleReservationConcurrencyTest`) confirmando que bloquea la fila de `Court` bajo carga
concurrente. **Se reutiliza esa forma de consulta por ese precedente probado — no porque la sola
forma del JPQL demuestre, por sí misma, qué filas terminan bajo `FOR UPDATE` en el SQL que
Hibernate genera.** Eso depende de cómo el dialecto MySQL de Hibernate traduce el `@Lock` sobre una
consulta con `JOIN` (si emite `FOR UPDATE` sin calificar — bloqueando ambas tablas del join — o
`FOR UPDATE OF court`, calificando solo una). Se confirma al implementar inspeccionando el SQL
real (`spring.jpa.show-sql=true`, ya disponible en el perfil `test`) y con un test de concurrencia
dedicado (`ReservationVsClassSessionConcurrencyTest`, sección 9.3) — no se asume de antemano acá.

No se lee el `ClassSlot` en esta consulta — recién se lee (y se bloquea, con su propio
`findByIdForUpdate`) en el segundo paso de `CreateClassSessionUseCase`.

### 7.3 REPEATABLE READ — lectura consistente vs. lectura bloqueante

El proyecto no fija explícitamente ningún nivel de aislamiento → corre con el default de
MySQL/InnoDB, REPEATABLE READ. Citando la referencia oficial de InnoDB sobre lecturas
([Consistent Nonlocking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html)):

- **Lectura consistente** (un `SELECT` común, sin `FOR UPDATE`/`FOR SHARE`): *"all consistent
  reads within the same transaction read the snapshot established by the first such read in that
  transaction"* — ve el estado confirmado al momento de esa primera lectura, y *"does not set any
  locks on the tables it accesses"*.
- **Lectura bloqueante** (`SELECT ... FOR UPDATE`, lo que hace `@Lock(PESSIMISTIC_WRITE)`): no usa
  el snapshot de la transacción — siempre ve el dato confirmado más reciente, y además toma el
  lock, bloqueando a otras transacciones que intenten lo mismo sobre esa fila.

Por qué `findCourtIdByClassSlotForUpdate` (lectura bloqueante) debe ser la primera lectura de la
transacción: si cualquier lectura *consistente* (un `SELECT` común, no bloqueante) ocurriera antes,
fijaría el snapshot de REPEATABLE READ para el resto de las lecturas consistentes de esa
transacción. Eso no cambia lo que ve la lectura bloqueante en sí (que igual ve el dato más
reciente, al margen del snapshot) — el riesgo real, ya documentado en el proyecto (Javadoc de
`CourtGateway.findCourtIdByReservationForUpdate`), es que si el **lock** se adquiere tarde, una
verificación de disponibilidad posterior hecha como lectura consistente (bajo un snapshot ya
fijado antes del lock) podría seguir sin ver una fila que otra transacción confirmó mientras
tanto. Este diseño no introduce un mecanismo nuevo — reutiliza el ya probado.

### 7.4 Coordinación entre flujos — por qué no hay deadlock por diseño de código

Deadlock requiere dos transacciones tomando dos recursos en orden inverso. Acá:

- `CreateClassSessionUseCase`: Court → ClassSlot (siempre, en ese orden).
- `AddPlayerToClassSlotUseCase`/`RemovePlayer`/`Pause`/`Reactivate`: solo ClassSlot.
- `BookReservationUseCase`/`RescheduleReservationUseCase`: solo Court.

Ningún flujo toma ClassSlot y después Court. No hay ciclo posible por diseño de código. **Esto no
es una garantía completa**: InnoDB usa gap locks bajo REPEATABLE READ, y dos `INSERT` concurrentes
en `court_occupancy` para slots *distintos* de la misma cancha pueden, en teoría, deadlockear por
locks de brecha del índice único — riesgo conocido de MySQL, no específico de este diseño, y que
solo un test de concurrencia real puede confirmar o descartar (sección 9.3). No se afirma la
garantía completa solo por `@Transactional` + orden de locks correcto.

### 7.5 `CourtEntity.classSlots` — decisión cerrada

**Se acepta** agregar a `CourtEntity`:

```java
@OneToMany(mappedBy = "court")
private Set<ClassSlotEntity> classSlots = new HashSet<>();
```

**Sin `CascadeType.REMOVE`/`ALL` ni `orphanRemoval`** — exactamente el mismo patrón que ya tiene
`CourtEntity.reservations` (verificado, sección 2: sin ningún `cascade` declarado). Ningún borrado
de `Court` debe arrastrar `ClassSlot` consigo — la protección real contra ese borrado es
`hasClassSlots()` (sección 5), no un cascade a nivel JPA.

---

## 8. Backfill y despliegue

### 8.1 Qué se considera "ocupado"

`status = 'RESERVADO'`, escrito **literalmente** en el SQL de `V9` — es el único valor de
`StatusReservation` con `occupiesSlot() == true` hoy. A diferencia del código Java de
`ReservationRepositoryAdapter` (que filtra dinámicamente por `occupiesSlot()` y automáticamente
seguiría un enum que creciera), **esta migración es una sentencia SQL estática**: si en el futuro
`StatusReservation` ganara un nuevo valor ocupante, este backfill puntual (ya ejecutado una vez,
en el pasado) no se actualiza solo ni se vuelve a correr — no se afirma ningún comportamiento
automático de este script frente a cambios futuros del enum.

### 8.2 Verificaciones — antes y después, no solo un argumento teórico

**Antes de correr el backfill:**

1. Confirmar que `uq_reservation_active_slot` (V2) existe realmente en el esquema objetivo — no
   asumir que sigue como V2 la dejó:
   ```sql
   SELECT * FROM information_schema.STATISTICS
   WHERE table_schema = DATABASE() AND table_name = 'reservation'
     AND index_name = 'uq_reservation_active_slot';
   ```
2. **Detectar duplicados en las coordenadas de reservas activas, como verificación explícita** —
   no basta con argumentar que no deberían existir:
   ```sql
   SELECT court_id, reservation_day, start_time, COUNT(*) AS n
   FROM reservation
   WHERE status = 'RESERVADO'
   GROUP BY court_id, reservation_day, start_time
   HAVING COUNT(*) > 1;
   ```
   Si esta consulta devuelve alguna fila, **no se continúa con el backfill** — se investiga la
   causa (por ejemplo, que el constraint del punto 1 no exista realmente, o haya sido alterado)
   antes de seguir. El argumento de que `uq_reservation_active_slot` ya impide esto en el uso
   normal de la aplicación sigue siendo válido como razón de fondo, pero no reemplaza esta
   verificación puntual, más barata que asumir.

**Después de correr el backfill:**

3. Correspondencia de cantidades:
   ```sql
   SELECT
     (SELECT COUNT(*) FROM court_occupancy WHERE source_type = 'RESERVATION') AS ocupaciones,
     (SELECT COUNT(*) FROM reservation WHERE status = 'RESERVADO') AS reservas_activas;
   ```
   Ambos números deben coincidir.
4. Correspondencia de datos, fila por fila (completa si el volumen lo permite, muestral si no):
   ```sql
   SELECT r.id, r.court_id, r.reservation_day, r.start_time,
          co.court_id AS occ_court_id, co.occupied_day, co.start_time AS occ_start_time
   FROM reservation r
   JOIN court_occupancy co ON co.source_type = 'RESERVATION' AND co.source_id = r.id
   WHERE r.status = 'RESERVADO'
     AND (co.court_id <> r.court_id OR co.occupied_day <> r.reservation_day OR co.start_time <> r.start_time);
   ```
   Debe devolver cero filas.

### 8.3 Atomicidad real de `V9` — MySQL no la garantiza como una sola transacción

**Corrección respecto de una versión anterior de este documento**: no es cierto que `CREATE TABLE
court_occupancy` y el `INSERT ... SELECT` de backfill formen una sola transacción atómica de
DDL+DML. MySQL/InnoDB ejecuta un **commit implícito** en cada sentencia DDL, `CREATE TABLE`
incluida — documentado en la referencia oficial:

> "Each of the statements in the following list (and any synonyms for them) implicitly ends any
> transaction active in the current session, as if you had done a `COMMIT` before executing the
> statement." — `CREATE TABLE` está en esa lista.
> — [MySQL 8.0 Reference Manual, 13.3.3 "Statements That Cause an Implicit Commit"](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)

Y, específicamente sobre `CREATE TABLE` en InnoDB:

> "The `CREATE TABLE` statement in InnoDB is processed as a single transaction. This means that a
> `ROLLBACK` from the user does not undo `CREATE TABLE` statements the user made during that
> transaction."
> — [MySQL 8.0 Reference Manual, "InnoDB and CREATE TABLE"](https://dev.mysql.com/doc/refman/8.0/en/innodb-create-table.html)

En `V9__add_court_occupancy.sql`: el `CREATE TABLE` comitea apenas se ejecuta — es irreversible
desde ese instante, corra o no con éxito el `INSERT ... SELECT` que sigue. Ese `INSERT ... SELECT`
sí es atómico como sentencia individual (se aplica entero o nada de él), pero es una transacción
**separada** de la creación de la tabla, no la misma.

**Estado parcial posible si `V9` falla a mitad de camino**: la tabla `court_occupancy` queda
**creada, vacía o parcialmente poblada**, mientras Flyway registra la migración como fallida en
`flyway_schema_history` (`success = 0`) y la aplicación no continúa arrancando (comportamiento por
defecto de Flyway ante una migración fallida, ya vigente para `V1`–`V3`, sin cambios).

**Cómo inspeccionar el estado real:**

```sql
-- Historial de migraciones, más reciente primero
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC;

-- ¿La tabla llegó a crearse, y con qué forma?
SHOW CREATE TABLE court_occupancy;

-- ¿Cuánto llegó a backfillearse, si algo?
SELECT COUNT(*) FROM court_occupancy;
SELECT COUNT(*) FROM reservation WHERE status = 'RESERVADO';
```

**`flyway repair` no revierte objetos de esquema ni corrige datos** — su propia documentación lo
deja explícito: para motores sin DDL transaccional (MySQL es uno de ellos), elimina la entrada de
migración fallida del historial y realinea checksums/descripciones, pero:

> "Remove any failed migrations [...] **User objects left behind must still be cleaned up
> manually.**"
> — [Flyway Reference — Repair command](https://github.com/flyway/flyway/blob/main/documentation/Reference/Commands/Repair.md)

Es decir: si `V9` dejó `court_occupancy` creada a medias, `repair` **no la borra ni la corrige** —
solo limpia el registro de Flyway para permitir un nuevo intento. **No se propone usar `flyway
clean` en ningún momento, ni borrar datos de forma automática** — cualquier limpieza de un objeto
parcial es una decisión manual y deliberada de una persona, dado que la tabla de origen del
backfill (`reservation`) tiene datos reales de producción.

**Procedimiento antes de reintentar** (manual, no automatizado):

1. Inspeccionar el estado real (consultas de arriba).
2. Determinar y corregir la causa raíz (¿error de sintaxis en el script? ¿la conexión se cortó a
   mitad del `INSERT`? ¿ya existía una `court_occupancy` de un intento anterior?).
3. Si `court_occupancy` quedó con datos parciales o incorrectos, una persona decide y ejecuta
   explícitamente el `DROP TABLE`/`DELETE`/`TRUNCATE` que corresponda — nunca disparado
   automáticamente por este documento ni por Flyway.
4. Si el archivo de migración cambió para corregir el bug, correr `flyway repair` para realinear
   su checksum antes de que Flyway acepte reaplicarlo.
5. `flyway repair` (limpia la entrada fallida de `flyway_schema_history`).
6. Reintentar la migración.

### 8.4 Ventana de despliegue explícita (diseño operativo — no autorización para desplegar)

**Corrección respecto de una versión anterior**: no se infiere ningún comportamiento de
despliegue de Railway a partir de que el README mencione "una sola instancia" — esa cita solo
respalda por qué el rate limiting en memoria (`LoginAttemptService`) funciona hoy; no dice nada
sobre cómo la plataforma maneja un restart, ni garantiza ausencia de una ventana de tráfico mixto
entre versiones. No se asume nada al respecto.

En su lugar, se diseña una ventana de mantenimiento explícita, independiente del mecanismo real de
la plataforma de despliegue (cómo se implementa cada paso ahí queda fuera de este documento):

1. **Detener nuevas escrituras.** El proyecto no tiene hoy ningún mecanismo de "modo mantenimiento"
   ni feature flags (verificado: nada de esto existe en `SecurityConfig` ni en ningún
   `@Configuration`) — el único mecanismo real disponible es dejar de servir tráfico al proceso de
   la aplicación. Se documenta como paso explícito y deliberado, no como algo que ocurra solo.
2. **Esperar a que terminen las transacciones en curso.** Ninguna transacción del proyecto es de
   larga duración (no hay `@Scheduled` ni jobs de fondo, verificado). Tras el paso 1, una espera
   breve alcanza; verificación explícita antes de continuar:
   ```sql
   SELECT * FROM information_schema.INNODB_TRX;
   ```
   no debe listar transacciones abiertas sobre `reservation`/`court`.
3. **Ejecutar las migraciones** (Flyway — al arranque de la nueva versión, o vía `flyway migrate`
   desde la CLI antes de arrancar la app, si se prefiere separar ambos pasos).
4. **Verificar esquema y backfill** — las consultas de la sección 8.2, revisadas por una persona.
5. **Arrancar y verificar la versión nueva** — únicamente la versión que ya incluye los cambios de
   la sección 6; verificación mínima (health check / equivalente de `contextLoads` en el entorno
   real) antes de habilitar tráfico.
6. **Habilitar tráfico.**

**Si la migración falla (paso 3): el servicio permanece detenido/sin tráfico.** No se propone, en
ningún caso, volver a levantar la versión anterior del código y reabrir tráfico mientras el
esquema quedó en un estado intermedio (sección 8.3) — la versión anterior no conoce
`court_occupancy` y seguiría aceptando reservas sin registrar su ocupación, rompiendo en silencio,
sin ningún error visible, la garantía que este cambio busca introducir. La única salida de una
migración fallida es diagnosticar (sección 8.3), corregir, y reintentar — con el servicio fuera de
línea mientras tanto.

Esto es diseño operativo para cuando se decida desplegar 1C — **no es autorización para ejecutar
ninguna migración ni comando contra una base real ahora.**

---

## 9. Plan de validación real

Tres niveles, con alcance y motor de base distintos — no se aprueban las garantías reales de 1C
con tests de un solo nivel.

### 9.1 Nivel A — mapeos y consultas de adapters (`@DataJpaTest`, H2 cuando alcanza)

`@DataJpaTest` por cada adapter nuevo, importando el adapter como bean adicional cuando haga falta
(`@Import(ClassSlotRepositoryAdapter.class)`, etc., ya que los adapters son `@Component`, no
repositorios Spring Data). H2 es aceptable acá porque lo que se valida es mapeo objeto-relacional
y forma de las consultas derivadas/`@Query` — nada de esto depende de semántica específica de
InnoDB. Ejemplos: reactivación de `ClassEnrollment` conserva el id (insertar una fila inactiva a
mano, verificar que `save()` la actualiza en vez de duplicar); filtrado por instructor y rango de
fechas en `ClassSessionRepositoryAdapter`; agregación de `countByStatusForSessions`.

### 9.2 Nivel B — wiring y flujos (contexto Spring completo)

`@SpringBootTest` verificando que los use cases de 1B, ya conectados a los adapters reales
(contra H2, perfil `test`), producen el resultado esperado de punta a punta — no releen mocks. El
primer chequeo de este nivel, y el más importante para 1C, es que **el contexto arranca** — ver
sección 10.6 sobre cuándo exactamente eso pasa a ser cierto (no antes de que existan los 8
adapters + `Clock`).

### 9.3 Nivel C — migraciones e integridad real (Testcontainers, Flyway habilitado, `ddl-auto=validate`)

**Distinto del molde de las 4 clases de concurrencia ya existentes** (`BookReservationConcurrencyTest`
y hermanas), que deshabilitan Flyway y usan `ddl-auto=create-drop` — ese molde prueba que el
*código* serializa correctamente contra un esquema que Hibernate generó de las entidades, no que
las *migraciones* `V1`–`V9` son correctas. Para este módulo hace falta, además, un nivel que sí
ejecute Flyway de punta a punta contra MySQL real:

```java
@DynamicPropertySource
static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    // ... credenciales del contenedor, igual que las 4 clases existentes ...
    registry.add("spring.flyway.enabled", () -> "true");           // habilitado, a diferencia de esas 4
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // valida contra las migraciones reales
}
```

No se usa únicamente `ddl-auto=create-drop` para aprobar `V4`–`V9` — este nivel es el que
realmente los ejercita.

**Casos mínimos:**

| Caso | Diseño |
|---|---|
| Migración desde una base vacía | Contenedor MySQL nuevo, Flyway corre `V1`–`V9` en el arranque del contexto; el contexto debe levantar. |
| Actualización desde V3 con reservas de distintos estados | Usar la API programática de Flyway (`Flyway.configure().dataSource(...).target("3").load().migrate()`) para dejar el esquema en el estado post-`V3`; insertar filas de `reservation` en los cuatro estados (`RESERVADO`, `CANCELADO`, `FINALIZADO`, `REPROGRAMADO`) vía SQL/JPA directo; correr `.target("9").load().migrate()`; verificar que `court_occupancy` solo contiene la(s) fila(s) `RESERVADO` (sección 8.1/8.2). |
| Validación del esquema contra las entidades | La combinación `spring.flyway.enabled=true` + `ddl-auto=validate` ya es la validación — si una entidad no coincide con lo que las migraciones crearon, el contexto no arranca. El test es, en sí, verificar que arranca. |
| Unicidad, rollback y concurrencia sobre el esquema real | Ver tabla siguiente — mismos escenarios que antes, pero corridos contra el esquema migrado por Flyway, no contra uno generado por `create-drop`. |

| Escenario de concurrencia | Diseño |
|---|---|
| Dos altas compiten por el último lugar | `AddPlayerToClassSlotConcurrencyTest` — N hilos, 1 lugar libre, exactamente un `ClassEnrollment` activo al final. |
| Dos creaciones de la misma sesión | `CreateClassSessionConcurrencyTest` — N hilos, mismo `classSlotId`+fecha. Como ambos intentos compiten por el **mismo** lock de `ClassSlot` (sección 7.1), quedan totalmente serializados entre sí — el perdedor debe fallar, de forma determinística, con `ClassSessionAlreadyExistsException` (el chequeo `existsByClassSlotIdAndDay` corre bajo ese mismo lock, antes que cualquier `INSERT`). |
| Reserva y clase compiten por cancha/fecha/hora (gana la reserva) | `ReservationVsClassSessionConcurrencyTest` — N hilos, mitad `BookReservationUseCase` mitad `CreateClassSessionUseCase` para el mismo court/día/hora, con ventaja de arranque para el lado `Reservation`. Se verifica: exactamente una operación exitosa en total; exactamente una fila en `court_occupancy` para ese slot, con `source_type='RESERVATION'` y `source_id` igual a la reserva ganadora; cero filas en `class_session`/`class_attendance`; los perdedores fallan con `SlotNotAvailableException` **o** con el conflicto traducido desde `DataIntegrityViolationException` (409 genérico, sección 6.7) — se acepta cualquiera de los dos, según en qué punto exacto perdieron la carrera (chequeo de aplicación vs. constraint de base) — no se fuerza un único tipo de excepción para todos los perdedores. |
| Reserva y clase compiten (gana la clase) | Mismo test, invertido — ventaja de arranque para `CreateClassSessionUseCase`. Se verifica lo simétrico: una fila en `court_occupancy` con `source_type='CLASS_SESSION'`; cero reservas `RESERVADO` para ese slot; los perdedores del lado `Reservation` fallan con `SlotNotAvailableException` o el conflicto de base equivalente. |
| Cancelar reserva libera su ocupación | Puede correr en Nivel B (H2) si no depende de locking real — solo verifica que el `DELETE` ocurrió. |
| Reprogramar mueve la ocupación atómicamente | Ídem — fila vieja desaparece, nueva aparece, `source_id` correcto. |
| Reprogramación fallida conserva reserva y ocupación anterior | Forzar el conflicto (crear una `ClassSession` en el slot destino antes de reprogramar); verificar que la reserva sigue `RESERVADO` en su slot original y la ocupación original sigue intacta. |
| Error al guardar asistencias revierte sesión y ocupación | **Mecanismo concreto, sin violar ninguna FK**: `@MockBean ClassAttendanceRepositoryPort` configurado para lanzar en `saveAll(...)`, dejando el resto de los puertos (`ClassSlotRepositoryPort`, `ClassSessionRepositoryPort`, `CourtOccupancyPort`) con sus adapters **reales** contra el contenedor. Tras la excepción, verificar por consulta directa (repositorios reales) que ni la fila de `class_session` ni la de `court_occupancy` quedaron persistidas — prueba el rollback real de esos dos escritos sin necesitar provocar una violación de integridad artificial. |
| Backfill conserva ocupación de reservas existentes | Cargar reservas `RESERVADO` directo por SQL contra el contenedor en estado pre-`V9`, correr `V9`, verificar `court_occupancy` resultante con las consultas de 8.2. |
| Eliminaciones no destruyen historial ni dejan huérfanos | Extender `CascadeDeletionRegressionTest` (mismo `@SpringBootTest @ActiveProfiles("test")`, ya existe) con: `deleteCourt_conClassSlot_falla`, `deleteBranch_conClassSlotEnAlgunaCourt_falla`, `deletePlayer_noEliminaSusClassEnrollments`, `deletePlayer_noEliminaSusClassAttendances`. Esta extensión es **necesaria** para verificar los cambios de la sección 5, no una ampliación por conveniencia — queda listada como archivo a modificar en la sección 10.2. |

No se usan mocks para probar exclusión mutua/rollback real de InnoDB — donde se usa un mock (el
caso de "error al guardar asistencias") es específicamente para *inyectar* el fallo, no para
*verificar* la garantía, que se comprueba contra los adapters reales.

**No se amplían ni refactorizan tests o adapters de otros módulos por conveniencia** —
`CascadeDeletionRegressionTest` es la única excepción, y es necesaria (verifica un cambio de
comportamiento real introducido por la sección 5), no cosmética.

### 9.4 Si Docker no está disponible

El Nivel C completo queda **pendiente de ejecutar**, no aprobado por omisión. No se declaran
cumplidas las garantías reales de la sección 6/7 (ocupación bidireccional, ausencia de deadlock,
comportamiento real de los locks) sin haber corrido, al menos una vez, contra MySQL real — los
Niveles A y B por sí solos no las prueban.

### 9.5 Regla de reporte

Antes de declarar cualquier resultado, correr la suite completa una vez sin los cambios de 1C
(baseline) y otra con ellos, y reportar la diferencia real — no asumir qué es "preexistente". El
baseline conocido y verificado al momento de escribir este documento es el de la sección 0: 362
tests, 0 failures, 30 errors.

---

## 10. Entrega

### 10.1 Archivos nuevos propuestos

**Entidades** (`model/entity/`): `InstructorEntity`, `ClassSlotEntity`, `ClassEnrollmentEntity`,
`ClassSessionEntity` (sin `courtId`), `ClassAttendanceEntity`, `CourtOccupancyEntity`.

**Repositorios Spring Data** (`persistence/repository/`): `InstructorRepository`,
`ClassSlotRepository`, `ClassEnrollmentRepository`, `ClassSessionRepository`,
`ClassAttendanceRepository`, `CourtOccupancyRepository`.

**Adapters** (`infrastructure/adapter/`): `InstructorGatewayAdapter`, `ClassSlotRepositoryAdapter`,
`ClassEnrollmentRepositoryAdapter`, `ClassSessionRepositoryAdapter`,
`ClassAttendanceRepositoryAdapter`, `ClassSlotCourtGatewayAdapter`, `PlayerRosterGatewayAdapter`,
`CourtOccupancyAdapter`.

**Configuración**: `config/ClockConfig.java` (paquete nuevo, un solo bean — decisión cerrada).

**Excepciones** (`exception/`): `CourtHasClassSlotsException`, `BranchHasClassSlotsException`.

**Migraciones** (`db/migration/`): `V4` a `V9` (sección 3.8).

**Tests de integración**: los de la sección 9 (Niveles A, B y C), incluida la extensión de
`CascadeDeletionRegressionTest`.

### 10.2 Archivos existentes a modificar, con motivo

| Archivo | Cambio | Motivo |
|---|---|---|
| `model/Rol.java` | Agregar `INSTRUCTOR` | Sección 3.1 / 4.2 |
| `model/entity/CourtEntity.java` | Agregar `Set<ClassSlotEntity> classSlots` (mappedBy="court", sin cascade) | Sección 7.2/7.5 |
| `domain/port/out/CourtRepositoryPort.java` | Agregar `hasClassSlots(Long courtId)` | Sección 5 |
| `domain/port/out/BranchRepositoryPort.java` | Agregar `hasClassSlots(Long branchId)` | Sección 5 |
| `infrastructure/adapter/CourtRepositoryAdapter.java` | Implementar `hasClassSlots` | Sección 5 |
| `infrastructure/adapter/BranchRepositoryAdapter.java` | Implementar `hasClassSlots` | Sección 5 |
| `application/usecase/court/DeleteCourtUseCase.java` | Chequear `hasClassSlots` | Sección 5 |
| `application/usecase/branch/DeleteBranchUseCase.java` | Chequear `hasClassSlots` | Sección 5 |
| `application/usecase/reservation/BookReservationUseCase.java` | Integrar `CourtOccupancyPort`, lanzando `SlotNotAvailableException` | Sección 6.1 |
| `application/usecase/reservation/CancelReservationUseCase.java` | Integrar `CourtOccupancyPort` | Sección 6.2 |
| `application/usecase/reservation/RescheduleReservationUseCase.java` | Integrar `CourtOccupancyPort`, lanzando `SlotNotAvailableException` | Sección 6.3 |
| `application/port/out/CourtOccupancyPort.java` | Agregar `registerForReservation`/`releaseForReservation` (sin `releaseForClassSession`) | Sección 6 |
| `src/test/.../service/CascadeDeletionRegressionTest.java` | Agregar los 4 casos de la sección 9.3 | Sección 5/9.3 — necesario, no cosmético |

`ClassSessionRepositoryPort` **no se modifica** — conserva su firma de 1B (sección 3.4). Ningún
otro archivo de 1A/1B se modifica.

### 10.3 Orden recomendado de implementación

1. `Rol.INSTRUCTOR` + `InstructorEntity`/`InstructorRepository`/`InstructorGatewayAdapter` +
   migración `V4`.
2. `ClassSlotEntity`/`ClassSlotRepository`/`ClassSlotRepositoryAdapter` + `V5`.
3. `ClassEnrollmentEntity`/adapter + `V6`.
4. `ClassSessionEntity`/adapter (sin `courtId`, sección 3.4) + `V7`.
5. `ClassAttendanceEntity`/adapter + `V8`.
6. `ClockConfig`.
7. `CourtEntity.classSlots` (sección 7.5) + `ClassSlotCourtGatewayAdapter` +
   `PlayerRosterGatewayAdapter`.
8. `CourtOccupancyEntity`/adapter + `V9` (tabla + backfill, con las verificaciones de 8.2).
9. **Checkpoint de arranque completo**: recién en este punto — con los 8 adapters de la sección 4
   y `Clock` existiendo todos — el `ApplicationContext` completo puede arrancar
   (`DeportLinkApplicationTests.contextLoads`). Ningún paso anterior a este alcanza por sí solo:
   `CreateClassSessionUseCase` (1B) ya depende de `ClassSlotCourtGateway` y `CourtOccupancyPort`
   en su constructor, así que el contexto no arranca hasta que exista el último de los 8.
10. `hasClassSlots` en `Court`/`BranchRepositoryPort`+adapters+`DeleteCourtUseCase`/
    `DeleteBranchUseCase` — puede ir en paralelo con los pasos 2–9, es independiente.
11. Los tres cambios de la sección 6 en `BookReservationUseCase`/`CancelReservationUseCase`/
    `RescheduleReservationUseCase`.
12. Tests de integración de la sección 9 — Nivel A y B en paralelo con los pasos anteriores según
    vayan existiendo sus adapters; Nivel C al final, una vez que el contexto arranca (paso 9) y la
    integración de Reservation (paso 11) está completa.

### 10.4 Decisiones pendientes (solo las bloqueantes)

Después de cerrar en este documento el contrato de `ClassSession` (sección 3.4), `CourtEntity
.classSlots` (sección 7.5), `ClockConfig` (sección 4.1) y el nivel de test de adapters
(`@DataJpaTest`, sección 9.1) — ninguna de esas cuatro queda pendiente. Queda una sola, y no
bloquea escribir ni probar el código de 1C, solo su despliegue real:

1. **Mecanismo concreto para "detener nuevas escrituras" en la plataforma de despliegue real**
   (sección 8.4, paso 1) — no se resuelve en este documento porque depende de una plataforma
   (Railway u otra) cuyo comportamiento no está verificado acá. Bloquea ejecutar la ventana de
   mantenimiento el día del despliegue real; no bloquea implementar entidades, adapters,
   migraciones ni tests.

### 10.5 Lo que NO se resuelve en 1C (fuera de alcance, reconfirmado)

Controllers, DTOs HTTP, `@PreAuthorize`, alta de instructores con credenciales, WhatsApp,
generación automática de sesiones, cancelación de `ClassSession`, edición de `ClassSlot`,
solapamiento general de intervalos, `releaseForClassSession`, soporte multi-instancia para el
despliegue del backfill.

### 10.6 Criterios verificables para aprobar 1C

1. `./mvnw test` completo (Niveles A y B, sección 9) — el número de `errors` por falta de
   contexto Spring vuelve a 0 (únicas fallas aceptables: las ya preexistentes por falta de Docker,
   si el entorno no lo tiene disponible).
2. `DeportLinkApplicationTests.contextLoads` pasa — **solo se espera que pase una vez completados
   los 8 adapters de la sección 4 más `Clock`** (orden de implementación, paso 9); no antes.
3. Los 129 tests de dominio+use cases de 1A/1B siguen pasando sin modificación de su código de
   producción.
4. Los tests de Nivel A (9.1) y Nivel B (9.2) pasan.
5. Los tests de **Nivel C** (9.3, Testcontainers + Flyway habilitado + `ddl-auto=validate`) pasan
   — en particular `ReservationVsClassSessionConcurrencyTest` en sus dos direcciones (gana reserva
   / gana clase) y el escenario de actualización desde `V3`. **Si el entorno no tiene Docker
   disponible, este criterio queda explícitamente pendiente — 1C no se declara verificado en sus
   garantías reales de concurrencia/migración sin él** (sección 9.4).
6. `CascadeDeletionRegressionTest`, extendido con los 4 casos de la sección 9.3, sigue en verde.
7. Ninguna migración de `V1` a `V3` fue editada; `V4`–`V9` corren limpio sobre una base con datos
   reales de `reservation`/`court`/`branch` restaurados de un dump (no solo contra una base de
   test vacía) — con las verificaciones de la sección 8.2 ejecutadas y revisadas.

---

**No se implementa nada de lo anterior sin aprobación explícita.**
