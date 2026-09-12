# Software Review

**Método:** `.claude/skills/software-review/SKILL.md` — auditoría de dos fases, de solo lectura.
**Rama auditada:** `fix/f16-add-schedule-lock` @ working tree, sobre HEAD `8330f69`.
**Fecha:** 11/09/2026. Es la **séptima** pasada de este Skill sobre este repositorio.

**Punto de partida — el código no cambió desde la auditoría anterior.** Verificado con
`find src docs pom.xml -newer docs/software-review-2026-09-09.md`: **cero archivos** de `src/` o
`pom.xml` fueron modificados después de que se escribiera `docs/software-review-2026-09-09.md`. El
working tree sigue exactamente igual: 28 archivos trackeados modificados y ~110 archivos nuevos sin
trackear (el módulo completo de gestión de clases, incluidos 36 archivos de test).

Esto cambia por completo dónde está el valor de esta pasada. Re-escribir los diecinueve hallazgos ya
confirmados contra código idéntico no aporta nada. Lo que sí aporta —y es exactamente lo que el propio
Skill pide en la Fase 2— es **buscar lo que las seis pasadas anteriores no vieron**. Esta auditoría se
organizó así:

1. **Re-verificación por muestreo** de los hallazgos abiertos (F1–F19), leyendo archivo y línea
   citados, no el resumen anterior. Resultado: todos se sostienen, ninguno cambia de severidad.
2. **Barrido nuevo y deliberado** sobre superficies que ninguna pasada anterior había recorrido con
   esta profundidad: validación de entrada en los DTOs *viejos* (no los del módulo nuevo), el
   contrato real del endpoint de disponibilidad, el límite de 72 bytes de BCrypt, la simetría entre
   endpoints hermanos (crear vs. actualizar, reservar vs. reprogramar), y las garantías de Capa 2
   (constraint de base) del módulo de clases. **Seis hallazgos nuevos (N1–N6), tres de ellos Medio.**
3. **Verificación empírica donde fue posible**, en vez de razonar sobre el papel: se ejecutó
   `BCryptPasswordEncoder.encode()` con contraseñas de 72/73/200 bytes contra el jar real que resuelve
   el proyecto; se ejecutaron los dos formateadores de hora que usan `AddScheduleUseCase` y
   `UpdateScheduleUseCase` contra los mismos valores de entrada; y se desensambló `MySQLDialect` de
   Hibernate 6.6.26 para resolver —en contra de la hipótesis inicial— una duda sobre el alcance real
   de los locks pesimistas. Los tres resultados están abajo con su comando y su salida.

**Contexto del proyecto (misma vara que las seis auditorías previas):** proyecto individual de
portfolio/aprendizaje, Java 21 + Spring Boot 3.5.5, arquitectura hexagonal + casos de uso, desplegado
en Railway como instancia única, sin tráfico de producción real.

**Sobre la suite de tests:** se ejecutó `./mvnw -o test` completo en esta sesión. Resultado real:
**440 tests ejecutados, 0 failures, 14 errors**. Los 14 errores son *todos* de la misma causa —
`IllegalStateException: Could not find a valid Docker environment` — en las 14 clases que usan
Testcontainers. No corrió ni una sola aserción de esas clases. Es un problema de infraestructura de
esta sesión (Docker no disponible), no del código. Con esa salvedad: **426 de 426 tests ejecutables
pasan**. Ver sección Testing para qué implica que justamente esas 14 clases sean las que no corrieron.

---

## Executive Summary

| Severidad | Confirmado | Riesgo potencial / Preventivo | Mejora opcional |
|---|---|---|---|
| 🔴 Critical | 0 | 0 | 0 |
| 🟠 High | 1 (F17) | 1 (F1) | 0 |
| 🟡 Medium | 5 (F3, F5-sport, **N1**, **N2**, **N3**) | 0 | 0 |
| 🔵 Low | 16 (14 previos + **N4**, **N6**) | 1 (**N5**) | 4 |

Los diecinueve hallazgos anteriores se sostienen sin cambios (el código es idéntico). Lo nuevo de esta
pasada son **N1–N6**, y el patrón que los une vale más que cualquiera por separado:

> **El módulo de clases —lo nuevo— valida su entrada con un rigor que el código viejo no tiene, y en
> tres casos concretos el código nuevo demuestra que el equipo conoce exactamente la trampa que el
> código viejo sigue pisando.**

El ejemplo más nítido es **N1**: `InstructorRequestDto` (escrito en esta misma rama) trae un
`@AssertTrue` dedicado llamado `isPasswordWithinEncoderLimit()` que rechaza contraseñas de más de 72
bytes UTF-8, porque BCrypt tira excepción arriba de ese límite. `UserRequestDto` —el DTO del que
heredan el registro de jugadores y el de dueños, que existe desde el principio del proyecto— tiene
`@Size(min = 8)` **sin máximo**. El resultado, verificado empíricamente: un `POST /api/players`
anónimo con una contraseña de 73 caracteres devuelve **HTTP 500**.

El mismo patrón se repite en la validación de fechas y horas (**N2**), en el contrato del endpoint de
disponibilidad (**N3**) y en la asimetría `@Future` vs. `@FutureOrPresent` entre reservar y reprogramar
(**N4**). En los cuatro casos el módulo nuevo hace lo correcto y el viejo no, lo que convierte la
recomendación en algo mucho más barato de lo habitual: **el patrón de referencia ya está escrito en
este mismo repositorio**, solo hay que replicarlo.

**N5** es de otra naturaleza: es el único punto del módulo de clases donde la disciplina de "dos capas"
que el proyecto aplica religiosamente (lock de aplicación + constraint de base) tiene una sola capa.
No hay carrera alcanzable hoy —se enumeraron los tres únicos escritores de `class_slot` y los dos que
pueden producir una fila ACTIVE lockean `Court` primero—, así que se reporta como **preventivo**, no
como bug activo.

---

## Architecture

Sin cambios respecto al 09/09 — código idéntico. Re-confirmado por lectura directa: dominio inmutable
(`record`s sin dependencia de Spring/JPA con factories que validan invariantes), puertos de dominio
(`domain/port/out`) separados de gateways de aplicación (`application/port/out`), casos de uso de una
sola responsabilidad, adapters como única capa que conoce JPA/MySQL.

Un punto que esta pasada verificó explícitamente y que vale documentar como *aprobado*, porque es el
tipo de cosa que una arquitectura hexagonal suele romper sin que nadie lo note: **la conversión de
`java.time.DayOfWeek` (lunes=1…domingo=7) a la convención de `DAYOFWEEK()` de MySQL (domingo=1…
sábado=7) vive en el adapter y solo en el adapter**. `ScheduleRepositoryAdapter.java:78` implementa
`toMySQLDayOfWeek(day) = (day.getValue() % 7) + 1`, con un comentario que explica por qué ese
conocimiento no puede subir al puerto ni al caso de uso. Se verificó la conversión valor por valor
(MONDAY=1 → 2 ✓, SUNDAY=7 → (7%7)+1 = 1 ✓): **es correcta**. Es la única fuga real de "la persistencia
es MySQL" en todo el proyecto y está contenida donde corresponde.

## SOLID

Sin hallazgos nuevos. Re-confirmado que `ClubRepositoryAdapter.updateExistingEntity()` sigue mezclando
responsabilidades sin impacto propio (igual que el 31/08 y el 09/09), y que
`MaintainRecurringClassesUseCase` sigue delegando en el bean inyectado `MaintainClassSlotScheduleUseCase`
en vez de en `this.metodo()` — evitando correctamente la auto-invocación de proxies que rompería el
`@Transactional` por-horario que su propio comentario promete.

El caso de `BusinessException` merece una mención positiva re-verificada en esta pasada: el
`@ExceptionHandler(BusinessException.class)` único, que lee el `HttpStatus` de la excepción misma en
vez de enumerarlas a mano, es open/closed genuino y está funcionando — las ~35 excepciones de negocio
del proyecto se traducen correctamente sin tocar `GlobalExceptionHandler`. **N1 y N2 no contradicen
esto**: son excepciones que *no* son de negocio (`IllegalArgumentException`, `DateTimeParseException`)
y por eso caen fuera de ese mecanismo — el diseño del handler es correcto, lo que falta es que esas
entradas nunca lleguen a producir una excepción de JDK en primer lugar.

## Clean Code

Sin cambios en lo ya auditado (F6, F7, F18, F19 re-confirmados — ver Findings). Una observación nueva,
reportada como **N6**: `GetClassSessionDetailUseCase` devuelve el roster de alumnos sin orden explícito,
mientras que su hermano `GetClassSlotDetailUseCase` sí ordena (`Comparator.comparing(ClassEnrollment::playerId)`).
Dos endpoints del mismo módulo, escritos para el mismo panel, con criterios distintos sobre lo mismo.

## DRY & Code Smells

Sin duplicación estructural nueva. El patrón "lockear primero, después leer/validar el resto" se
repite literalmente en ocho casos de uso del módulo de clases, pero —como ya argumentó la auditoría del
09/09— es la misma clase de repetición deliberada ya aceptada para `Book`/`Reschedule`/`Delete*`: un
lock pesimista no es abstraíble sin ocultar exactamente la garantía que cada método debe declarar.

Lo que sí es duplicación con deriva real —y por eso es un hallazgo, no una nota de estilo— es la
**lógica de parseo de hora duplicada con dos formatos incompatibles** entre `AddScheduleUseCase` y
`UpdateScheduleUseCase` (**N2**). Es el caso de libro que este Skill describe: la misma regla expresada
dos veces, que ya divergió.

## Testing

**Se ejecutó la suite completa en esta sesión** (`./mvnw -o test`):

```
[ERROR] Tests run: 422, Failures: 0, Errors: 14, Skipped: 0     (salida agregada de Surefire)
Agregando los 74 reportes de target/surefire-reports/*.txt: tests=440 failures=0 errors=14 skipped=0
```

Los 14 errores son **todos** la misma causa, verificada uno por uno en los reportes de Surefire:
`IllegalStateException: Could not find a valid Docker environment` (el primero) /
`Previous attempts to find a Docker environment failed. Will not retry.` (los 13 siguientes). Ninguna
aserción falló. Las 14 clases afectadas son exactamente las que dependen de Testcontainers:

`AddPlayerToClassSlotConcurrencyTest`, `BackfillFromV3IntegrationTest`, `ClassRecurrenceIntegrationTest`,
`ClassSessionRollbackIntegrationTest`, `CourtOccupancyReservationLifecycleTest`,
`FlywayMigrationIntegrationTest`, `InstructorApiIntegrationTest`,
`ReservationVsClassSessionConcurrencyTest`, `ReservationUniqueSlotConstraintTest`,
`AddScheduleConcurrencyTest`, `BookReservationConcurrencyTest`,
`BookReservationVsDeleteBranchConcurrencyTest`, `BookReservationVsDeleteCourtConcurrencyTest`,
`RescheduleReservationConcurrencyTest`.

**Esto no se reporta como hallazgo, pero sí como una limitación que conviene tener presente**: la lista
de arriba no es un subconjunto cualquiera. Son *precisamente* las pruebas que verifican las garantías
más caras del proyecto —no doble reserva bajo concurrencia real, el `UNIQUE` de V2, el rollback de
`CreateClassSessionUseCase`, la migración de Flyway contra MySQL real, la carrera de F16 recién
cerrada—. En un entorno sin Docker, la suite no distingue "Docker no está" de "una garantía de
concurrencia se rompió": las 14 aparecen como errores rojos indistinguibles. El README (línea 265) sí
advierte que hace falta Docker, así que no es una omisión de documentación; la decisión de fallar
ruidosamente en vez de saltear con `assumeTrue(...)` tiene un argumento legítimo a favor (nunca dar
verde sin haber verificado) y otro en contra (el modo de falla es ambiguo). Es una decisión de estilo
de CI, no un defecto — se deja explícito para que sea una decisión y no un accidente.

**Verificación de una afirmación del README.** El README (línea 265) afirma: *"Estado actual: 239 tests,
0 failures, 0 errors"*. Se verificó contra el estado **commiteado** (no contra el working tree):
`git ls-files 'src/test/**/*.java'` da 38 archivos con **242 anotaciones de test** en HEAD — la cifra
del README sigue siendo esencialmente correcta para el código commiteado. **La afirmación se sostiene
hoy**, pero quedará desactualizada en ~215 tests en el momento exacto en que este branch se commitee
(36 archivos de test nuevos sin trackear). No es un hallazgo todavía; es un ítem de checklist para el
commit.

F11 y F12 re-confirmados sin cambios: cero tests directos para `JwtFilter`/`LoginAttemptService`/
`AuthService`/`UserDetailsServiceImpl`, y `OwnerServiceTest` sigue mal ubicado en la raíz del paquete
de tests.

**Cobertura faltante detectada en esta pasada**, relacionada con los hallazgos nuevos: no existe ningún
test —ni unitario ni de integración— que ejercite las rutas de entrada inválida de N1, N2 y N3. En
particular, `ScheduleRepositoryAdapter.toMySQLDayOfWeek()` no tiene cobertura directa de ningún tipo
(no hay `ScheduleRepositoryAdapterTest`), y no puede tenerla vía integración porque
`existsReservationForDay` usa `FUNCTION('DAYOFWEEK', ...)`, nativa de MySQL, que H2 en modo estándar no
resuelve. La conversión está bien (se verificó a mano, ver Architecture), pero está sin red.

## Security

**N1 es el hallazgo de seguridad de esta pasada**, y conviene ser preciso sobre qué es y qué no es: no
es una vulnerabilidad de autenticación ni una fuga de datos. Es un **endpoint público sin autenticar
que devuelve 500 ante una entrada perfectamente legítima**, y que además escribe un stack trace
completo a nivel ERROR por cada intento. Un atacante puede generar entradas de log arbitrariamente
(no hay rate limiting sobre el registro, solo sobre el login), y un usuario legítimo con un gestor de
contraseñas que genere una passphrase larga simplemente no puede registrarse y no recibe ninguna pista
de por qué. Ver el detalle y la verificación empírica en Findings.

Se re-verificó el resto de la superficie de seguridad sin encontrar nada nuevo:

- **Patrón anti-IDOR**: consistente en todo el proyecto. `CancelReservationUseCase.belongsTo()`,
  `Actor.canAccess()` en los ocho casos de uso del módulo de clases, y los tres beans de autorización
  (`CourtAuthorization`, `BranchAuthorization`, `ClubAuthorization`) colapsan "no autorizado" en "no
  encontrado" en vez de devolver 403. Verificado en particular
  `CourtRepository.existsByCourtAndOwner` (JOIN court→branch→club→owners): como `owners.id` ES
  `users.id` (herencia JOINED, misma PK), un PLAYER nunca puede colisionar con un `owner.id`.
- **`@PreAuthorize` en los cuatro controllers nuevos del módulo de clases**: re-verificado que los
  cuatro que resuelven `@CurrentActor Actor` están anotados `hasRole('INSTRUCTOR')` a nivel de clase
  sin excepción para ADMIN, y que `InstructorAdminController` es `hasRole('ADMIN')`. El soporte ADMIN
  que tiene `Actor.canAccess()` sigue sin ser alcanzable por HTTP — preparación deliberada y
  documentada (`docs/class-management-stage-1d-api-design.md:40-41`), no un gap.
- **`CurrentActorArgumentResolver`**: deriva el rol de las authorities del JWT, nunca del body.
  Correcto. (Su registro con `new` sigue siendo F18.)
- **`POST /api/instructor/player-lookup`**: permite a cualquier INSTRUCTOR autenticado confirmar si un
  email dado corresponde a un jugador registrado (404 si no). Es un oráculo de enumeración real, pero
  **no se reporta como hallazgo**: los instructores los crea exclusivamente un ADMIN
  (`InstructorAdminController`), son una población chica y confiable, y la funcionalidad es necesaria
  para armar el grupo de una clase. Se deja anotado para que la decisión sea consciente si alguna vez
  el alta de instructores se abre al autoservicio.
- **`/v3/api-docs/**` y `/swagger-ui/**` en `permitAll()`**: re-verificado y, como ya concluyó la
  auditoría del 09/09, no se reporta — exponer documentación de API sin auth es práctica común y
  deliberada.
- **CORS**: `validateCorsAllowedOrigins()` falla el arranque con mensaje claro si
  `CORS_ALLOWED_ORIGINS` no está seteada. Buen fail-fast, re-confirmado.
- **F1 (rate limiter de login en memoria y su manejo de IP de proxy)** sigue abierto sin cambios, y
  sigue siendo el ítem que más depende de un hecho externo no verificable desde el repositorio.

## Concurrency

**La disciplina de locking del proyecto se re-verificó entera y se sostiene.** Todos los caminos de
escritura que compiten por una `Court` toman `SELECT ... FOR UPDATE` sobre esa fila como **primera**
lectura de la transacción, antes de cualquier `SELECT` plano — el orden importa por el snapshot de
REPEATABLE READ de MySQL, y está correctamente respetado en `BookReservationUseCase:46`,
`RescheduleReservationUseCase:61`, `DeleteCourtUseCase:23`, `DeleteBranchUseCase:69`,
`AddScheduleUseCase:45`, `CreateClassSlotUseCase:51`, `CreateClassSessionUseCase:77`,
`MaintainClassSlotScheduleUseCase:30` y `ReactivateClassSlotUseCase:27`.

**Orden de locks — verificado sin encontrar ciclos.** Se enumeraron los dos recursos lockeables
(`Court` y `ClassSlot`) y todos los casos de uso que toman ambos, buscando específicamente un ciclo
ABBA:

| Caso de uso | Orden |
|---|---|
| `CreateClassSessionUseCase`, `MaintainClassSlotScheduleUseCase`, `ReactivateClassSlotUseCase` | Court → ClassSlot |
| `CreateClassSlotUseCase` | Court (→ ClassSlot vía `maintainSchedule`, misma transacción) |
| `PauseClassSlotUseCase`, `AddPlayerToClassSlotUseCase`, `RemovePlayerFromClassSlotUseCase`, `Confirm`/`CancelAttendanceUseCase` | Solo ClassSlot |

**Ningún camino toma ClassSlot antes que Court.** No hay deadlock ABBA posible entre estos dos
recursos con el código actual. `SyncFutureClassRoster` (`@Transactional(propagation = MANDATORY)`, se
llama con el ClassSlot ya lockeado) no toca `Court` — se verificó línea por línea.

**Una hipótesis que se investigó y resultó falsa, y que vale documentar porque es el tipo de cosa que
una lectura superficial daría por buena en cualquiera de las dos direcciones.**
`CourtGatewayAdapter.findByIdForUpdate()` ejecuta
`findByIdForUpdateWithRelations` — un `@Lock(PESSIMISTIC_WRITE)` sobre una query con
`JOIN FETCH c.sport JOIN FETCH c.branch b JOIN FETCH b.address`. La hipótesis era grave: en MySQL, un
`SELECT ... FOR UPDATE` con joins bloquea filas de **todas** las tablas recorridas, así que cada
reserva estaría lockeando también la fila de `sport` — serializando globalmente *todas* las reservas de
pádel de la plataforma entera, y abriendo la puerta a deadlocks con cualquier flujo que tome esos
locks en otro orden. Se buscó deliberadamente la evidencia que la contradijera, desensamblando el
dialecto real que resuelve el proyecto (Hibernate ORM 6.6.26.Final, confirmado en el log de la corrida
de tests):

```
$ javap -p -c -cp hibernate-core-6.6.26.Final.jar org.hibernate.dialect.MySQLDialect
  boolean supportsAliasLocks();
    Code:  0: iconst_1      <-- devuelve true
           1: ireturn
  public java.lang.String getForUpdateString(java.lang.String);
    Code:  0: aload_0
           1: invokevirtual  supportsAliasLocks:()Z
           4: ifeq  16
           7: aload_1 ... makeConcatWithConstants   <-- " for update of <alias>"
```

`MySQLDialect.supportsAliasLocks()` devuelve `true`, así que Hibernate emite **`for update of c`** —
el lock recae solo sobre la fila raíz de `court`, no sobre `sport`/`branch`/`address`. **La hipótesis
queda descartada por evidencia directa**, no por omisión. Lo mismo aplica a
`ClassSlotRepository.findByAttendanceIdForUpdate` (subquery escalar sobre `class_attendance`): el lock
es `for update of s`, sobre `class_slot` únicamente, que es exactamente lo que su Javadoc promete.

**F4 sigue abierto sin cambios**: `UpdateScheduleUseCase` y `DeleteScheduleUseCase` siguen sin el lock
de `Court` que `AddScheduleUseCase` ganó con F16. Re-verificado leyendo ambos archivos completos en
esta pasada — ninguno llama `findByIdForUpdate`.

**N5** es el único hallazgo nuevo de esta sección, y es preventivo: ver Findings.

## Database & Indexes

**Correlación consulta↔índice re-hecha para el módulo de clases** (las tablas viejas ya las cubrió en
profundidad `docs/audit-database-2026-08-26.md`, cuyo análisis se re-leyó y no se duplica acá). Se
enumeraron los métodos de repositorio de las cinco tablas nuevas, se trazó cada uno a un llamador vivo,
y se verificó cobertura por regla de prefijo izquierdo. **Sin hallazgos — se documenta el pase:**

| Query viva | Filtra por | Índice que la sirve | ✓ |
|---|---|---|---|
| `ClassSlotRepository.findByCourt_IdAndDayOfWeekAndActiveStatus` | court_id, day_of_week (+ status en memoria) | `idx_class_slot_court_day (court_id, day_of_week)` | ✓ |
| `ClassSlotRepository.findAllByInstructor_Id` | instructor_id | `idx_class_slot_instructor` | ✓ |
| `ClassSlotRepository.existsByCourt_Id` | court_id | `idx_class_slot_court_day` (prefijo) | ✓ |
| `ClassSlotRepository.existsByCourt_Branch_Id` | branch_id vía join a court | `idx_court_branch_active` (prefijo) + FK | ✓ |
| `ClassSessionRepository.findScheduledAfter` | class_slot_id, session_date | `uq_class_session_slot_date` (prefijo) | ✓ |
| `ClassSessionRepository.existsByClassSlot_IdAndSessionDate` | class_slot_id, session_date | `uq_class_session_slot_date` (completo) | ✓ |
| `ClassSessionRepository.findByInstructorAndDateRange` | class_slot.instructor_id + rango de fecha | `idx_class_slot_instructor` → `uq_class_session_slot_date` | ✓ |
| `ClassEnrollmentRepository.findByClassSlot_IdAndPlayer_Id` | class_slot_id, player_id | `uq_class_enrollment_slot_player` (completo) | ✓ |
| `ClassEnrollmentRepository.findByClassSlot_IdAndActiveTrue` / `countBy...` | class_slot_id | `uq_class_enrollment_slot_player` (prefijo) | ✓ |
| `ClassAttendanceRepository.findByClassSession_IdAndPlayer_Id` | session_id, player_id | `uq_class_attendance_session_player` (completo) | ✓ |
| `ClassAttendanceRepository.findByClassSession_Id` / `countGroupedByStatus` | session_id (IN) | `uq_class_attendance_session_player` (prefijo) | ✓ |
| `CourtOccupancyRepository.existsByCourt_IdAndOccupiedDayAndStartTime` | court_id, día, hora | `uq_court_occupancy_slot` (completo) | ✓ |
| `CourtOccupancyRepository.deleteBySourceTypeAndSourceId` | source_type, source_id | `idx_court_occupancy_source` (completo) | ✓ |

**Dos matices, ninguno de ellos hallazgo:**

- `CourtOccupancyRepository.findConflictingDates` filtra `court_id` (igualdad) + `start_time` (igualdad)
  + `occupied_day` (rango). El índice es `(court_id, occupied_day, start_time)`: el motor puede usar
  `court_id` + el rango de `occupied_day`, pero la igualdad de `start_time` queda después de un rango y
  no se aprovecha como filtro de índice. Es subóptimo en teoría y **irrelevante en la práctica** con
  cualquier volumen razonable de `court_occupancy`; no se recomienda tocar el índice sin `EXPLAIN`
  contra datos reales, que esta auditoría no ejecuta.
- `ClassSlotRepository.findIdsByActiveStatus` (la que alimenta el scheduler diario) no tiene índice
  sobre `active_status` y hace un scan completo de `class_slot` una vez por día. Con una tabla de
  horarios de clase, eso es correcto y el índice sería contraproducente (baja selectividad, dos valores).

**Migraciones: comentario vs. ejecución real.** Se leyeron las nueve migraciones en orden, contrastando
lo que cada comentario promete contra lo que el SQL ejecuta. **Una discrepancia, ya reportada:**
`V1__baseline.sql:70` dice textualmente *"V2 agrega PRIMARY KEY (owner_id, club_id) previa limpieza de
duplicados"* — y `V2__add_unique_reservation_slot.sql` no hace nada de eso: agrega la columna generada
y el `UNIQUE` de reservas, y nada sobre `owner_club`. La promesa nunca se cumplió. **Es exactamente
F2**, que ya está abierto; esta pasada aporta la evidencia adicional de que no es solo "falta un
constraint" sino "hay una migración que afirma por escrito que ese constraint existe desde V2".

**Deriva entidad ↔ migración: verificada, sin deriva.** Se contrastaron las anotaciones `@Table`/
`@UniqueConstraint`/`@Index`/`@Column(nullable)` de `ClassSlotEntity`, `ClassSessionEntity`,
`ClassEnrollmentEntity`, `ClassAttendanceEntity`, `CourtOccupancyEntity` e `InstructorEntity` contra el
DDL real de V4–V9: **coinciden una a una**, nombres de índice incluidos. Con
`spring.jpa.hibernate.ddl-auto=validate` en producción, esa coincidencia es lo que permite que la app
arranque; con el perfil `dev` (`ddl-auto=update`, opt-in explícito) es lo que evita que Hibernate
invente un índice paralelo.

**Integridad de datos — barrido completo sobre columnas con semántica de identidad real:**

| Tabla · columna | Constraint en base | Chequeo en aplicación | Estado |
|---|---|---|---|
| `users.email` | `uq_users_email` ✓ | `emailExists` / `existsByEmail` | Dos capas ✓ |
| `owners.cuil` | `uq_owners_cuil` ✓ | `existsByCuil` | Dos capas ✓ |
| `clubs.cuit`, `clubs.legal_name` | `uq_clubs_cuit`, `uq_clubs_legal_name` ✓ | sí | Dos capas ✓ |
| `tickets.reservation_id` | `uq_tickets_reservation_id` ✓ | — | Base ✓ |
| `reservation` (slot activo) | `uq_reservation_active_slot` (columna generada) ✓ | lock + `findBookedSlots` | Dos capas ✓ |
| `class_enrollment (slot, player)` | `uq_class_enrollment_slot_player` ✓ | lock + `findByClassSlotIdAndPlayerId` | Dos capas ✓ |
| `class_session (slot, fecha)` | `uq_class_session_slot_date` ✓ | lock + `existsByClassSlotIdAndDay` | Dos capas ✓ |
| `class_attendance (session, player)` | `uq_class_attendance_session_player` ✓ | — | Base ✓ |
| `court_occupancy (court, día, hora)` | `uq_court_occupancy_slot` ✓ | lock + `existsOccupancy` | Dos capas ✓ |
| **`class_slot (court, día, hora)` activos** | **ninguno** | lock + `ClassRecurrenceAdapter.hasConflict` | **Una capa → N5** |
| `owners.dni` | ninguno | `existsByDni` | Una capa → **F2/F3** |
| `sport.name_sport` | ninguno | `findBy().isPresent()` | Una capa → **F5** |
| `owner_club (owner, club)` | ninguno | chequeo en memoria | Una capa → **F2** |

La fila resaltada es el aporte nuevo de esta pasada (**N5**). Las tres últimas son F2/F3/F5, sin
cambios.

**Ciclo de vida de `court_occupancy`, trazado completo** (ninguna auditoría anterior lo había recorrido
de punta a punta). `registerForReservation` se llama en `BookReservationUseCase:94` y
`RescheduleReservationUseCase:121`; `releaseForReservation` en `CancelReservationUseCase:62` y
`RescheduleReservationUseCase:120`; `registerForClassSession` en `CreateClassSessionUseCase:130`. No
existe `releaseForClassSession` — decisión documentada (cancelar una clase completa está fuera del MVP).
Se buscó específicamente un camino que dejara una fila huérfana bloqueando una cancha para siempre:
**no se encontró ninguno alcanzable.** `DeleteCourtUseCase` no puede dejar filas huérfanas porque
rechaza el borrado si la cancha tiene *cualquier* reserva o *cualquier* `class_slot`, y toda fila de
`court_occupancy` proviene necesariamente de una de esas dos cosas (la de `CLASS_SESSION` requiere un
`class_slot` vivo por FK). Lo verificado acá es que la garantía se sostiene por construcción, no por
casualidad.

**F17 re-verificado y sostenido sin cambios**: la detección de conflicto sigue siendo por coincidencia
exacta de `(court, día, hora de inicio)` —`existsByCourt_IdAndOccupiedDayAndStartTime` y
`ClassRecurrenceAdapter.hasConflict` comparando `s.getStartTime().equals(slot.startTime())`—, y
`ClassSlotRequestDto` sigue permitiendo cualquier `startTime` y cualquier duración de 1 a 1440 minutos
sin atarlos a la grilla de `Schedule` de la cancha. Sigue siendo el hallazgo Alto/Confirmado abierto.

## Performance

Sin hallazgos nuevos. Se verificaron explícitamente los puntos donde el módulo nuevo podría haber
introducido N+1 o consultas sin límite, y **todos pasan**:

- `GetClassSessionsByInstructorUseCase`: enriquece N sesiones con exactamente 3 consultas batch
  (`findAllByIds` de slots, `countByStatusForSessions` con `GROUP BY`, y un `HashMap` de caché para
  canchas). No hay una consulta por sesión.
- `GetClassSessionDetailUseCase`: una sola consulta batch (`playerRosterGateway.findByIds`) para todos
  los nombres del roster.
- **Consulta sin límite superior: hipótesis investigada y descartada.**
  `ClassSessionRepository.findByInstructorAndDateRange` acepta `to = null` como "sin límite", lo que
  parecía una consulta no acotada. Se trazó al **único** llamador HTTP vivo
  (`InstructorClassSessionController:24-35`) y ahí se ve que el controller nunca pasa `null`: si el
  cliente omite `to`, usa `start.plusDays(27)`, y rechaza con `InvalidClassRequestException` cualquier
  rango de 93 días o más. **La ruta no acotada no es alcanzable por HTTP.** Es exactamente el tipo de
  hipótesis que el Skill pide verificar contra el llamador real antes de reportarla.
- `CourtOccupancyRepository.findConflictingDates` sí devuelve todas las fechas futuras coincidentes sin
  tope, y filtra el día de la semana en memoria. Es deliberado y está comentado ("No horizon limit:
  existing bookings months ahead must also be respected"); el resultado es de una fila por semana
  coincidente, no un volumen que crezca con la tabla.

F8 (listados admin sin paginar) y F9 (historial de reservas sin orden explícito) siguen abiertos sin
cambios. **N6** agrega una segunda instancia del patrón de F9 en el módulo nuevo.

## Production Readiness

Sin cambios en lo ya auditado: F15 (sin Actuator ni configuración explícita de logging) re-confirmado.
`RecurringClassesScheduler` sigue dependiendo del mismo supuesto de instancia única de Railway ya
documentado para el rate limiter, con `@ConditionalOnProperty` como válvula de escape — sin cambios
respecto al 09/09, no se reporta.

**Lo que esta pasada agrega a esta sección es N1 y N2 vistos desde el ángulo operativo**: los dos
producen HTTP 500 con `log.error("Unexpected error: ...", ex)` y stack trace completo. El comentario
de `handleGenericException` dice, textualmente: *"Red de seguridad final: a partir de este refactor, ya
no debería recibir excepciones de negocio — solo errores de programación reales, no anticipados."* La
intención es correcta y el diseño del handler también; el problema es que **hoy ese handler sí recibe
tráfico de entradas de usuario perfectamente normales**, lo que convierte al log de ERROR en ruido y
esconde los errores de programación de verdad entre falsos positivos. Cerrar N1 y N2 no es solo una
mejora de la respuesta HTTP: es lo que hace que ese log vuelva a significar lo que dice que significa.

Configuración por entorno re-verificada sin deriva: `application.properties` fija
`ddl-auto=validate` (Flyway manda), `application-dev.properties` activa `update` solo bajo perfil
explícito, y ni `DB_URL`/`DB_PASSWORD`/`JWT_SECRET` ni `CORS_ALLOWED_ORIGINS` tienen default
hardcodeado. No hay ningún camino por el que la configuración de conveniencia de desarrollo se cuele en
producción por accidente.

---

## Findings

Se listan solo los hallazgos **nuevos** de esta pasada, con el template completo. Los diecinueve
anteriores (F1–F19) se re-verificaron y se sostienen sin cambios — ver la sección siguiente.

### N1 — 🟡 Medium — Una contraseña de más de 72 bytes en el registro público de jugadores devuelve HTTP 500, porque `UserRequestDto` no tiene el tope que el DTO nuevo de instructores sí tiene

- **Confianza:** Confirmado (verificado empíricamente, no por lectura)
- **Evidencia:**
  - `UserRequestDto.java:26-28` — `@NotBlank @Size(min = 8, message = "...") private String password;`
    **sin máximo**. `PlayerRequestDto` y `OwnerRequestDto` extienden esta clase y heredan esa
    validación tal cual.
  - `RegisterPlayerUseCase.java:33` — `String encodedPassword = passwordEncoder.encode(dto.getPassword());`
  - `PasswordConfig.java:13` — `return new BCryptPasswordEncoder();`
  - `SecurityConfig.java:92` — `.requestMatchers(HttpMethod.POST, "/api/players").permitAll()`, y
    `PlayerController.java:40-41` (`@PostMapping` sobre `@RequestMapping("/api/players")`) **sin
    `@PreAuthorize`**: el endpoint es anónimo.
  - `GlobalExceptionHandler.java:113-121` — `@ExceptionHandler(Exception.class)` devuelve 500 y loguea
    `log.error("Unexpected error: {}", ex.getMessage(), ex)`. `IllegalArgumentException` no extiende
    `BusinessException`, así que cae acá.
  - **Verificación empírica** contra el jar exacto que resuelve el proyecto
    (`spring-security-crypto-6.5.3`, confirmado en `~/.m2`):
    ```
    matches(72) = false          encode(72) ok
    matches(73) = false          encode(73) THROWS: IllegalArgumentException -> password cannot be more than 72 bytes
    matches(200) = false         encode(200) THROWS: IllegalArgumentException -> password cannot be more than 72 bytes
    ```
    El desensamblado confirma dónde nace: `BCrypt.hashpw(byte[], String, boolean)` compara
    `arraylength` contra `bipush 72` y lanza `IllegalArgumentException`. Nótese que **`matches()` no
    lanza** — el login no está afectado, solo el alta.
  - **Contraste dentro del mismo branch:** `InstructorRequestDto.java:101-109` sí lo maneja:
    `@Size(min=8,max=72)` más un `@AssertTrue` explícito llamado `isPasswordWithinEncoderLimit()` con
    el mensaje *"La contraseña no debe superar 72 bytes UTF-8"*.
- **Ubicación:** `POST /api/players` (público, sin autenticar) y `POST /api/owners` →
  `OwnerGatewayAdapter.java:41` (solo ADMIN, mismo mecanismo).
- **Impacto:** Un usuario que se registra con una passphrase de más de 72 bytes UTF-8 —una passphrase
  tipo diceware de 12 palabras ronda los 70; con acentos o emojis el límite se alcanza con muchos
  menos de 72 *caracteres*, porque el tope es en bytes— recibe `500 {"message": "Ocurrió un error
  inesperado"}`. No puede registrarse, y no hay nada en la respuesta que le indique qué corregir; lo
  más probable es que reintente con la misma contraseña. Del lado del servidor, cada intento escribe
  un stack trace completo a nivel ERROR. Como el endpoint es anónimo y no tiene rate limiting (el
  `LoginAttemptService` cubre solo el login), un tercero puede generar ese volumen de logs a voluntad,
  y —más importante en la práctica— los errores de programación reales quedan enterrados entre estos
  falsos positivos, que es justamente lo que el comentario de `handleGenericException` dice que no
  debería pasar.
- **Explicación:** BCrypt, por diseño del algoritmo, solo considera los primeros 72 bytes de la
  entrada. Hasta Spring Security 6.2 el encoder truncaba en silencio; desde 6.3 lanza
  `IllegalArgumentException` en vez de truncar (una mejora: falla ruidoso en vez de darle al usuario
  una falsa sensación de fortaleza). El proyecto resuelve 6.5.3, así que está del lado que lanza. El
  equipo claramente conoce el límite —lo documentó y lo validó en el DTO nuevo—; lo que faltó fue
  aplicar el mismo tope al DTO viejo del que heredan los otros dos registros.
- **Recomendación:** Agregar a `UserRequestDto.password` el mismo par de validaciones que ya tiene
  `InstructorRequestDto`: `@Size(min = 8, max = 72)` más el `@AssertTrue` que cuenta bytes UTF-8 (el
  `@Size` cuenta *caracteres*, así que por sí solo no alcanza para entradas no-ASCII — por eso el DTO
  de instructores tiene las dos). Con eso el caso se convierte en un 400 con `MethodArgumentNotValidException`,
  que el handler ya traduce correctamente. **Trade-off a decidir, no obvio:** el mensaje de error
  "la contraseña no puede superar 72 bytes" es raro de explicarle a un usuario final; la alternativa
  —pre-hashear con SHA-256 antes de BCrypt, que elimina el límite— es una decisión criptográfica con
  sus propias implicancias (invalida todos los hashes existentes salvo que se migre progresivamente) y
  no vale la pena para este proyecto. La validación es la opción correcta acá.

### N2 — 🟡 Medium — La API de agendas parsea día y hora desde strings sin validar: entradas inválidas devuelven 500, y `POST` y `PUT` aceptan formatos de hora incompatibles entre sí

- **Confianza:** Confirmado (verificado empíricamente)
- **Evidencia:**
  - `ScheduleRequestDto.java:13-20` — `day`, `openingTime` y `closingTime` son `String` con
    **únicamente** `@NotBlank`. Ningún `@Pattern`, ningún deserializador estricto (a diferencia de
    `ClassSlotRequestDto`, que sí usa `StrictIntegerDeserializer` y tipos `LocalTime`/`DayOfWeek`
    reales).
  - `AddScheduleUseCase.java:70-77` — `DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm")`,
    `DayOfWeek.valueOf(dto.getDay().toUpperCase())`, `LocalTime.parse(dto.getOpeningTime(), timeFormat)`.
  - `UpdateScheduleUseCase.java:33-34` — `LocalTime.parse(openingNew)` / `LocalTime.parse(closingNew)`,
    **sin formateador** → usa `ISO_LOCAL_TIME`.
  - `ScheduleController.java:62-70` — el `PUT` recibe `@RequestParam String openingNew` y
    `@RequestParam String closingNew`, sin ninguna anotación de validación.
  - `GlobalExceptionHandler.java:25-27` — `handleMalformedRequest` cubre
    `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` y
    `HandlerMethodValidationException`. **`DateTimeParseException` e `IllegalArgumentException` no
    están**, y ninguna extiende `BusinessException` → caen en `handleGenericException` → 500.
  - **Verificación empírica** de los dos formateadores contra las mismas entradas:
    ```
    ADD  parse('9:00') OK          UPD  parse('9:00') FAIL: DateTimeParseException
    ADD  parse('09:00') OK         UPD  parse('09:00') OK
    ADD  parse('09:00:00') FAIL    UPD  parse('09:00:00') OK
    ADD  parse('25:00') FAIL       UPD  parse('25:00') FAIL
    DayOfWeek.valueOf("LUNES") FAIL: IllegalArgumentException
    ```
  - **Segunda instancia del mismo mecanismo**, fuera de agendas: `OwnerRequestDto.dateOfBirth` es
    `@NotBlank String` y `OwnerGatewayAdapter.java:36` / `RegisterOwnerUseCase.java:40` hacen
    `LocalDate.parse(command.dateOfBirth(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))`. Un ADMIN que
    envíe `"1990-05-10"` en vez de `"10/05/1990"` recibe 500.
- **Ubicación:** `POST /api/schedules/court/{idCourt}`, `PUT /api/schedules/{idSchedule}/court/{idCourt}`
  (ambos alcanzables por el OWNER de la cancha o por un ADMIN, vía
  `@courtAuthorization.isOwnerOfCourt`), y `POST /api/owners` (solo ADMIN).
- **Impacto:** Dos consecuencias distintas, ambas reales.
  **(a) 500 en vez de 400.** Cualquier dueño de club que mande `"day": "LUNES"` en vez de `"MONDAY"`,
  o `"openingTime": "9 AM"`, recibe `500 "Ocurrió un error inesperado"` sin ninguna indicación de qué
  campo está mal ni qué formato se espera. Con `day` como string libre y sin documentación de formato
  en el DTO, es el error más probable que cometa un cliente nuevo, no un caso de borde.
  **(b) Asimetría entre crear y actualizar — el caso más difícil de diagnosticar.** Un cliente crea la
  agenda con `"openingTime": "9:00"` y el `POST` funciona. El mismo cliente, con el mismo valor,
  llama al `PUT` para corregir el horario y recibe **500**. El valor es válido para un endpoint del
  recurso e inválido para el otro; el sistema no explica la diferencia en ningún lado. En sentido
  inverso, `"09:00:00"` actualiza bien pero no puede crear.
- **Explicación:** El parseo vive en la capa de aplicación en vez de en el borde HTTP, así que el
  framework de validación —que ya está en uso y ya funciona para el resto— nunca ve estos valores. Los
  dos formatos divergieron porque cada caso de uso eligió el suyo por separado: `AddScheduleUseCase`
  con un `DateTimeFormatter.ofPattern("H:mm")` explícito, `UpdateScheduleUseCase` con el
  `LocalTime.parse` por defecto (ISO, hora de dos dígitos obligatoria). Es la definición de duplicación
  que ya derivó: la misma regla escrita dos veces, con resultados distintos.
- **Recomendación:** Dos cambios, el primero cierra las dos mitades del problema y el segundo es la
  red de seguridad:
  1. **Tipar el borde HTTP en vez de parsear adentro.** Cambiar `ScheduleRequestDto` a
     `DayOfWeek day` y `LocalTime openingTime/closingTime` (con `@JsonFormat(pattern = "HH:mm")`, mismo
     criterio que `ReservationRequestDto` ya usa), y los dos `@RequestParam String` del `PUT` a
     `@DateTimeFormat`. Jackson/Spring pasan a rechazar el valor inválido *antes* del controller, con
     `HttpMessageNotReadableException`/`MethodArgumentTypeMismatchException` — que
     `handleMalformedRequest` **ya traduce a 400 correctamente**. Con eso los dos endpoints comparten
     un único formato por construcción y la asimetría deja de ser posible. El mismo tratamiento aplica
     a `OwnerRequestDto.dateOfBirth`. **Trade-off:** es un cambio de contrato de API — un cliente que
     hoy manda `"9:00"` al `POST` empezará a recibir 400 en vez de 201, así que conviene decidir
     conscientemente qué formato queda como el único válido (`"HH:mm"` es el que ya usa el resto del
     proyecto) y comunicarlo.
  2. **Independientemente de lo anterior**, agregar `DateTimeParseException` e `IllegalArgumentException`
     a la lista de `handleMalformedRequest`. Es una línea, y convierte en 400 cualquier otro parseo que
     se agregue en el futuro sin que nadie se acuerde de este hallazgo.

### N3 — 🟡 Medium — `GET /api/reservations/available` ofrece como disponibles turnos que ya pasaron, incluidos días enteros en el pasado

- **Confianza:** Confirmado
- **Evidencia:**
  - `GetAvailableSlotsUseCase.java:47-51` — la lista se arma con `slotConfig.generateSlots()` y **tres**
    filtros: `!booked.contains(slot)`, `!recurring.contains(slot)`, `!courtOccupancy.existsOccupancy(...)`.
    **Ningún filtro temporal.**
  - `ScheduleGateway.SlotConfig.generateSlots()` (`ScheduleGateway.java:25-34`) genera la grilla entera
    desde `openingTime` hasta `closingTime`, sin conocer la fecha ni la hora actual.
  - `ReservationController.java:70-75` — `@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate day`,
    **sin `@FutureOrPresent`**, y el controller no está anotado `@Validated`.
  - **Contraste dentro del mismo recurso:** `ReservationRequestDto.java:17-20` —el DTO con el que se
    reserva— sí tiene `@NotNull @FutureOrPresent` sobre `day`.
  - **Contraste con el módulo nuevo, que sí lo hace bien:**
    `MaintainClassSlotScheduleUseCase.java:37` descarta explícitamente la ocurrencia de hoy si ya
    empezó (`if (!LocalDateTime.of(day, slot.startTime()).isAfter(now)) day = day.plusWeeks(1);`), y
    `CourtOccupancyRepository.findConflictingDates` y `ClassSessionRepository.findScheduledAfter`
    filtran "solo futuro" en la query.
- **Ubicación:** `GET /api/reservations/available?courtId={id}&day={fecha}` — accesible a cualquier
  usuario autenticado (`@PreAuthorize("isAuthenticated()")`).
- **Impacto:** Dos escenarios concretos, ambos deterministas (no dependen de concurrencia ni de
  volumen):
  **(a) Turnos de hoy que ya pasaron.** Cancha abierta de 08:00 a 22:00 con turnos de una hora. A las
  18:00, `GET /available?day=<hoy>` devuelve `[08:00, 09:00, ..., 21:00]` — las diez primeras ya
  pasaron. Si el usuario elige 09:00, el `POST` pasa la validación de `@FutureOrPresent` (la *fecha*
  es hoy) y pasa `isValidSlot()`, y recién muere en el dominio: `Reservation.create()` →
  `TimeSlot.isFuture()` → `InvalidTimeRangeException` → **422 "La fecha seleccionada ya pasó"**.
  **(b) Días enteros en el pasado.** `GET /available?day=2020-01-01` devuelve la grilla completa de esa
  cancha como disponible (ninguna reserva vieja la bloquea si esos turnos ya quedaron FINALIZADO o
  CANCELADO, que no ocupan slot). Acá el `POST` sí se rechaza limpio en el borde, por
  `@FutureOrPresent`.
  El efecto neto es que el endpoint que existe precisamente para responder *"¿qué puedo reservar?"*
  responde que sí a cosas que el sistema va a rechazar. No hay corrupción de datos ni 500 — el daño es
  que el contrato del endpoint no es cierto, y que el usuario descubre el error recién al confirmar.
- **Explicación:** La disponibilidad se calcula como "grilla completa menos lo ocupado", y "ya pasó"
  nunca se modeló como una forma de no estar disponible. La responsabilidad quedó, de hecho, en el
  dominio (`TimeSlot.isFuture()`), que solo corre en el camino de escritura — el de lectura nunca la
  consulta. Es notable que el módulo de clases, escrito después, sí trata "ya empezó" como un filtro
  de primera clase en los tres lugares donde importa: el criterio correcto ya está en el repositorio,
  simplemente no se aplicó hacia atrás.
- **Recomendación:** Filtrar en `GetAvailableSlotsUseCase` los turnos no futuros, usando el bean
  `Clock` que el proyecto ya inyecta (`ClockConfig`) para que el filtro sea testeable — un cuarto
  `.filter(...)` que, cuando `day` es hoy, descarte los `slot` que no sean posteriores a
  `LocalTime.now(clock)`, y que devuelva lista vacía si `day` ya pasó. Complementariamente, agregar
  `@FutureOrPresent` al `@RequestParam day` (con `@Validated` en el controller) para que una fecha
  pasada se rechace con 400 en el borde en vez de devolver una lista vacía silenciosa — las dos cosas
  comunican mejor que cualquiera sola. **Trade-off menor a decidir:** si el negocio quiere permitir
  consultar la grilla de un día pasado para fines de visualización (un calendario que muestre el
  historial), entonces la validación del `@RequestParam` no corresponde y solo aplica el filtro
  temporal para el día de hoy — es una decisión de producto, no técnica.

### N4 — 🔵 Low — Se puede reservar para hoy pero no reprogramar para hoy: `@Future` vs. `@FutureOrPresent` entre dos DTOs hermanos

- **Confianza:** Confirmado
- **Evidencia:**
  - `ReservationRequestDto.java:17-20` — `@NotNull @FutureOrPresent private LocalDate day;`
  - `RescheduleRequestDto.java:17-20` — `@NotNull @Future(message = "La nueva fecha debe ser futura")
    private LocalDate newDay;`
  - `ReservationController.java:58-68` — el método de reprogramación recibe
    `@RequestBody @Valid RescheduleRequestDto`, así que la restricción se aplica efectivamente.
  - `RescheduleReservationUseCase.java:49-104` — el caso de uso **no tiene ninguna regla de "no el
    mismo día"**: valida pertenencia, slot válido, disponibilidad y ocupación, y construye la nueva
    reserva con `Reservation.create()`, que usa `TimeSlot.isFuture()` — semántica de
    *FutureOrPresent*, no de *Future*.
- **Ubicación:** `PUT /api/reservations/{reservationId}/reschedule`
- **Impacto:** Un jugador con una reserva hoy a las 10:00 que quiere moverla a hoy a las 19:00 recibe
  `400 "La nueva fecha debe ser futura"`, aunque el turno destino esté a nueve horas de distancia y
  aunque ese mismo jugador *sí* podría reservar ese turno de las 19:00 desde cero con un `POST`. La
  única salida es cancelar y volver a reservar — que es exactamente el flujo de dos pasos que el
  Javadoc de `RescheduleReservationUseCase` describe como descartado a propósito, porque *"dos requests
  independientes no son atómicos: otro jugador puede ocupar el nuevo slot entre ambas llamadas"*. El
  DTO empuja al usuario justo hacia el camino que el caso de uso fue diseñado para evitar.
- **Explicación:** No parece una regla de negocio deliberada: no está documentada en ningún lado, el
  caso de uso no la conoce, y el dominio usa la semántica opuesta. Lo más probable es que sea una
  elección de anotación hecha por separado en cada DTO, sin contrastar una con la otra.
- **Recomendación:** Cambiar `@Future` por `@FutureOrPresent` en `RescheduleRequestDto.newDay`, para
  que el borde HTTP coincida con lo que el dominio ya permite (`TimeSlot.isFuture()` sigue rechazando
  la hora pasada dentro del día de hoy, así que no se abre ningún agujero). **Alternativa igual de
  válida, si el mismo día efectivamente debe estar prohibido:** dejar `@Future` y mover la regla al
  dominio o al caso de uso, con un mensaje que explique el porqué — lo que no tiene sentido es que la
  regla exista solo en una anotación de un DTO y contradiga al agregado.

### N5 — 🔵 Low (Preventivo) — `class_slot` es el único invariante central del módulo de clases sin respaldo de constraint en la base

- **Confianza:** Preventivo — el mecanismo (falta de Capa 2) es real y verificado; el escenario de
  falla **no es alcanzable hoy**
- **Evidencia:**
  - `V5__add_class_slot.sql` — la tabla tiene PK, dos `KEY` no únicos
    (`idx_class_slot_instructor`, `idx_class_slot_court_day`) y dos FK. **Ningún `UNIQUE`.**
    `ClassSlotEntity.java:21-26` lo confirma del lado de la entidad: `@Table` declara solo los dos
    `@Index`, sin `uniqueConstraints`.
  - `ClassRecurrenceAdapter.java:30-33` — la regla "no puede haber dos horarios activos en la misma
    cancha, el mismo día de la semana, a la misma hora" se evalúa **solo en memoria**:
    `slots.findByCourt_IdAndDayOfWeekAndActiveStatus(...).stream().anyMatch(s -> ... s.getStartTime().equals(slot.startTime()))`.
  - **Enumeración completa de los escritores** (`grep` sobre `classSlotRepository.save`): son
    exactamente tres. `CreateClassSlotUseCase.java:70` (lockea `Court` en la línea 51, antes de
    `validateRecurrence` y del `save`), `ReactivateClassSlotUseCase.java:39` (lockea `Court` en la
    línea 27, antes de todo) y `PauseClassSlotUseCase.java:39` (guarda una fila **INACTIVE**, no puede
    crear el conflicto). Los dos únicos caminos que pueden producir una fila ACTIVE toman el lock de
    `Court` como primera operación → quedan serializados entre sí. **La Capa 1 está completa.**
  - **Contraste con el resto del proyecto**, que sí usa dos capas para cada invariante equivalente:
    `V2__add_unique_reservation_slot.sql` lo dice en su propio encabezado —*"Defensa en profundidad
    para la garantía central 'no doble reserva': un índice único a nivel de base de datos, además del
    lock pesimista de aplicación"*— y `V9` hace lo mismo con `uq_court_occupancy_slot`.
- **Ubicación:** tabla `class_slot`; regla implementada en `ClassRecurrenceAdapter.hasConflict()`.
- **Impacto:** Ninguno hoy — se buscó específicamente un camino de escritura que evadiera el lock de
  `Court` y no existe. El riesgo es hacia adelante y es concreto: el día que se agregue un
  `UpdateClassSlotUseCase` (editar hora o día de un horario existente está hoy fuera del MVP pero es
  la siguiente funcionalidad natural), o un panel de ADMIN, o una importación masiva, quien lo escriba
  tiene que *acordarse* de tomar el lock de `Court` **antes** de cualquier lectura y en ese orden
  exacto. Si se olvida, dos requests concurrentes pueden dejar dos `ClassSlot` activos sobre la misma
  cancha, mismo día y misma hora — y la base los acepta sin chistar, porque no hay nada que lo impida.
  A partir de ahí, `findActiveStarts()` devuelve el mismo horario dos veces y dos grupos de alumnos
  quedan asignados al mismo lugar a la misma hora.
- **Explicación:** No es un descuido conceptual sino, con toda probabilidad, una consecuencia de que
  MySQL no soporta índices únicos parciales: el invariante correcto es "único **entre las filas
  ACTIVE**", y un `UNIQUE (court_id, day_of_week, start_time)` liso rompería el caso legítimo de pausar
  un horario y crear otro en la misma franja. Lo notable es que **el proyecto ya resolvió exactamente
  ese problema**, en V2, con el truco estándar: una columna generada que vale `NULL` salvo cuando la
  fila está en el estado que importa, y el `UNIQUE` sobre esa columna (InnoDB excluye los `NULL` de la
  verificación de unicidad).
- **Recomendación:** Replicar literalmente el patrón de V2 en una migración nueva sobre `class_slot`:
  una columna generada `active_slot_court_id` que sea `court_id` cuando `active_status = 'ACTIVE'` y
  `NULL` en caso contrario, y un `UNIQUE KEY (active_slot_court_id, day_of_week, start_time)`. El
  código de aplicación no cambia: el chequeo de `hasConflict()` sigue siendo la Capa 1 que devuelve un
  error de negocio legible, y el constraint solo actúa como red si algún camino futuro se saltea el
  lock (y `GlobalExceptionHandler` ya traduce `DataIntegrityViolationException` a 409, así que el
  fallback tampoco sería un 500). **Trade-offs honestos:** (a) hay que verificar contra la base real
  que no existan ya filas que violen el constraint antes de aplicarla —hoy, con el módulo sin
  desplegar, la tabla debería estar vacía, que es el momento más barato posible para hacerlo—; (b) esta
  garantía es de coincidencia *exacta* de hora de inicio, con la misma limitación que describe **F17**:
  no detecta solapamientos parciales, y no debe confundirse con una solución a ese hallazgo. Es
  defensa en profundidad de lo que ya se garantiza, no una garantía nueva. (c) Si el equipo decide
  primero resolver F17 restringiendo `ClassSlot.startTime` a la grilla de `Schedule`, conviene hacer
  ambos cambios juntos: el constraint es más valioso cuando la coincidencia exacta equivale de nuevo a
  solapamiento real.

### N6 — 🔵 Low — El roster de una clase se devuelve sin orden explícito, mientras que el endpoint hermano del mismo panel sí ordena

- **Confianza:** Confirmado
- **Evidencia:**
  - `GetClassSessionDetailUseCase.java:161,179-181` — `classAttendanceRepository.findByClassSessionId(...)`
    y después `attendances.stream().map(this::toView).toList()`, **sin ningún `sorted(...)`**.
  - `ClassAttendanceRepository.java:16` — `List<ClassAttendanceEntity> findByClassSession_Id(Long classSessionId);`
    es una derived query **sin `ORDER BY`** y sin parámetro `Sort`.
  - **Contraste con el hermano:** `GetClassSlotDetailUseCase.java:26` sí ordena explícitamente —
    `members.stream().sorted(Comparator.comparing(ClassEnrollment::playerId))`.
  - `GetClassSessionsByInstructorUseCase.java:74-77` también ordena explícitamente (día, hora, id). Es
    decir: de los tres endpoints de lectura del módulo, dos ordenan y uno no.
- **Ubicación:** `GET /api/instructor/class-sessions/{sessionId}` → campo `attendees`.
- **Impacto:** El orden de los alumnos en el detalle de una clase no está garantizado por nada. En la
  práctica, con una tabla chica, MySQL suele devolver las filas en orden de clave primaria (que acá
  coincide con el orden de alta), así que lo más probable es que hoy se vea estable — **y ese es
  justamente el problema**: es estable por accidente, no por contrato. Un cambio de plan del optimizador
  (que un índice distinto pase a ser elegido cuando la tabla crezca), o una reorganización de InnoDB,
  puede reordenar la lista sin aviso. Para el instructor que usa el panel para pasar lista, una lista
  que cambia de orden entre recargas es confuso; y cualquier test que afirme sobre el orden de este
  endpoint es potencialmente inestable por la misma razón. El impacto real es menor —nadie pierde datos
  y nada se rompe—, y por eso es Low y no Medium.
- **Explicación:** Es la misma clase de problema que **F9** (historial de reservas sin orden explícito),
  reaparecido en código nuevo: la ausencia de `ORDER BY` no se nota mientras el motor devuelva algo
  razonable, así que no hay nada que fuerce a notarla al escribir el código. Que los otros dos endpoints
  del mismo módulo sí ordenen sugiere un olvido puntual, no un criterio distinto.
- **Recomendación:** Ordenar explícitamente, eligiendo el criterio que tenga sentido para pasar lista —
  lo más útil para el instructor es por nombre de alumno, pero eso obliga a ordenar después de resolver
  los nombres (`playerRosterGateway.findByIds`), no en la query. Lo más barato y suficiente es replicar
  el criterio del hermano: `.sorted(Comparator.comparing(ClassAttendance::playerId))` sobre
  `attendances`, o agregar `OrderByPlayer_IdAsc` al nombre de la derived query. **Trade-off:** ordenar
  por nombre es mejor UX pero se paga en memoria de aplicación y deja el orden fuera del índice;
  ordenar por `player_id` es gratis (el índice `uq_class_attendance_session_player` ya lo tiene en ese
  orden) pero el orden resultante no significa nada para el usuario. Para el MVP, `player_id` alcanza y
  es consistente con el resto del módulo.

---

## Findings re-verificadas sin cambios (F1–F19)

El código auditado es **byte por byte el mismo** que el de `docs/software-review-2026-09-09.md`
(verificado con `find -newer`, cero archivos de `src/` o `pom.xml` modificados después). Los
diecinueve hallazgos se sostienen sin cambios de severidad ni de confianza. Se re-leyeron en esta
pasada, contra el archivo y línea citados, los siguientes —no se dan por buenos a partir del resumen
anterior—:

- **F2** — `owner_club` sin PK/UNIQUE. Re-verificado en `V1__baseline.sql:65-73`, **con evidencia
  adicional nueva**: el comentario de V1 afirma que V2 agregaría esa PK, y V2 no la agrega. Ver
  Database & Indexes.
- **F4** — `UpdateScheduleUseCase`/`DeleteScheduleUseCase` sin el lock de `Court`. Re-verificado
  leyendo ambos archivos completos: ninguno llama `findByIdForUpdate`.
- **F6** — Métodos muertos en `CourtRepository`. Re-verificado con `grep` por método:
  `findByNameAndBranchIdAndSportId`, `findByIdAndBranch_Id`, `findByCourtWithSchedule` y
  `findBySport_Id` siguen con **cero** llamadores en `src/main`.
- **F7** — `ErrorResponse.validationErrors` nunca poblado. Re-verificado: el campo existe
  (`ErrorResponse.java:23`) y `GlobalExceptionHandler:67-78` construye un `Map` local que termina
  concatenado dentro de `message`, sin asignarse nunca al campo del DTO.
- **F10** — `spring-boot-starter-oauth2-client` sin uso. Re-verificado: `grep -r oauth2 src/main/java`
  devuelve **0** resultados, con el dependency todavía en `pom.xml`.
- **F11, F12** — Cobertura de tests de seguridad y ubicación de `OwnerServiceTest`. Re-verificados
  contra el árbol de tests.
- **F17** — Coincidencia exacta de horario en vez de solapamiento real. Re-verificado contra
  `CourtOccupancyRepository:30`, `ClassRecurrenceAdapter:29-38` y `ClassSlotRequestDto`. Sigue siendo
  el único hallazgo Alto/Confirmado abierto.
- **F18** — `CurrentActorArgumentResolver` con `new` en `WebConfig`. Re-verificado en
  `WebConfig.java:141-144` (`resolvers.add(currentUserIdArgumentResolver)` inyectado, seguido de
  `resolvers.add(new ...CurrentActorArgumentResolver())`) y `CurrentActorArgumentResolver.java:11`
  (`@Component`).
- **F19** — Javadocs desactualizados. Re-verificado: `Actor.java:62-67` sigue diciendo que la
  integración con el resolver *"queda para una etapa futura"* mientras
  `CurrentActorArgumentResolver.java` existe en el mismo branch, y `ActorRole.java:110-116` sigue
  describiendo la incorporación de INSTRUCTOR a `model.Rol` como *"diferida"* cuando `Rol.INSTRUCTOR`
  ya está.
- **F1, F3, F5, F8, F9, F13, F14, F15** — Sin re-lectura línea por línea adicional a la ya hecha el
  09/09: ningún archivo involucrado cambió, y el `find -newer` lo confirma de forma global en vez de
  caso por caso.

**F16 sigue resuelto** — no vuelve a la lista de hallazgos abiertos.

---

## Audit Validation

Fase 2 sobre los seis hallazgos nuevos. Para cada uno se re-trazó el flujo desde cero (releyendo a los
llamadores, no el resumen de la Fase 1), se enumeraron *todos* los llamadores en vez del primero, y se
buscó deliberadamente la evidencia que lo contradijera.

- **N1 — Confirmed.** Re-trazado completo: se verificó que `PlayerController` está mapeado en
  `/api/players`, que su `@PostMapping` no tiene `@PreAuthorize`, y que `SecurityConfig:92` lo declara
  `permitAll()` — las tres condiciones necesarias para que el endpoint sea anónimo. Se buscó lo que lo
  refutaría: si existiera un `@ControllerAdvice`, un `@InitBinder` o un handler específico de
  `IllegalArgumentException` que interceptara antes del handler genérico (no existe: se enumeraron los
  nueve `@ExceptionHandler` de `GlobalExceptionHandler` y ninguno lo cubre), o si `BCryptPasswordEncoder`
  truncara en silencio en vez de lanzar (se ejecutó contra el jar real: **lanza**). Se verificó además
  que `matches()` **no** lanza, lo que acota correctamente el impacto al alta y excluye el login —un
  matiz que la hipótesis inicial no tenía y que la validación corrigió—. Severidad Medium sostenida:
  es determinista, alcanzable sin autenticación, y el usuario legítimo queda sin salida; no sube a High
  porque no hay pérdida ni exposición de datos y la superficie de abuso es ruido de logs, no un vector
  de compromiso.
- **N2 — Confirmed.** Re-trazado desde el controller: ambos endpoints son alcanzables por el OWNER de
  la cancha o un ADMIN vía `@courtAuthorization.isOwnerOfCourt`, verificado leyendo
  `CourtAuthorization` y su query. Se buscó lo que lo refutaría: si algún deserializador
  personalizado interceptara los strings antes del caso de uso (`ScheduleRequestDto` no tiene ninguno
  — a diferencia de `ClassSlotRequestDto`, que sí usa `StrictIntegerDeserializer`), o si
  `DateTimeParseException` descendiera de alguna excepción ya manejada (desciende de
  `DateTimeException` → `RuntimeException`; ninguna está en la lista de `handleMalformedRequest`). La
  asimetría entre `POST` y `PUT` se comprobó ejecutando ambos formateadores en vez de razonarla. El
  hallazgo **ganó alcance** durante esta fase: el barrido de `grep '\.parse('` sobre todo `src/main`
  reveló una segunda instancia del mismo mecanismo en `OwnerRequestDto.dateOfBirth`, que la Fase 1 no
  había mirado. Severidad Medium sostenida.
- **N3 — Confirmed.** Re-leído `GetAvailableSlotsUseCase` entero buscando cualquier filtro temporal
  que la Fase 1 hubiera pasado por alto (no hay: son exactamente tres filtros, todos de ocupación), y
  re-leído `ReservationController:70-75` para confirmar que el `@RequestParam` no tiene restricción.
  Se siguió el camino hasta el final para describir el impacto con precisión en vez de exagerarlo:
  reservar un turno pasado de hoy **no** produce un 500 sino un 422 con mensaje correcto, y una fecha
  pasada se rechaza limpio en el `POST` por `@FutureOrPresent` — el defecto está acotado a la
  *respuesta del endpoint de lectura*, no a la integridad de la reserva. Severidad Medium sostenida:
  es determinista, ocurre todos los días a partir del primer turno vencido, y afecta al endpoint
  central del flujo principal de la app.
- **N4 — Confirmed.** Se buscó específicamente la evidencia de que fuera una regla de negocio
  deliberada —lo que lo convertiría en un no-hallazgo—: no está documentada en el README ni en ningún
  doc de diseño, `RescheduleReservationUseCase` no la implementa ni la menciona, y el agregado
  (`Reservation.create` → `TimeSlot.isFuture()`) usa la semántica **opuesta**. Tres evidencias
  independientes apuntando a que la anotación contradice al dominio, no que lo refleje. Severidad Low
  sostenida: el usuario tiene una salida (cancelar y reservar), aunque sea justamente la que el diseño
  quería evitar.
- **N5 — Preventivo (así se reportó desde el inicio, y la validación lo confirma en vez de
  degradarlo).** El punto entero de este hallazgo era no describir una carrera que no existe. Se
  enumeraron **todos** los llamadores de `save` sobre `ClassSlot` (tres, vía `grep`, no el primero que
  apareció) y se verificó uno por uno el orden de operaciones: los dos que pueden producir una fila
  ACTIVE lockean `Court` antes de cualquier lectura; el tercero solo escribe INACTIVE. La conclusión
  —"el safeguard falta pero el escenario no es alcanzable hoy"— es exactamente la categoría que el
  método define como preventiva, y es la que cambia la prioridad: no hay que correr a arreglarlo, hay
  que arreglarlo **antes** de escribir el `UpdateClassSlotUseCase`.
- **N6 — Confirmed, severidad ajustada a la baja durante esta fase.** La Fase 1 lo había pensado como
  "orden no determinístico". La validación matizó el impacto: en MySQL/InnoDB, un `SELECT` sin
  `ORDER BY` sobre una tabla chica típicamente devuelve las filas en orden de PK, así que en la
  práctica hoy se ve estable. Eso **no** lo invalida (la estabilidad no está garantizada por nada y
  puede cambiar con el plan del optimizador), pero sí baja el impacto real de "la lista se ve
  desordenada" a "la lista se ve ordenada por accidente". Se reporta como Low con esa precisión
  explícita en el campo de Impacto, en vez de afirmar un desorden que el usuario probablemente no
  observe hoy.

**Hipótesis investigadas en Fase 1 o Fase 2 y descartadas** (no se reportan como hallazgos, pero se
documentan para que la próxima pasada no las vuelva a recorrer):

1. **Lock pesimista ampliado a `sport`/`branch`/`address` por los `JOIN FETCH`** — descartada por
   desensamblado de `MySQLDialect` (`supportsAliasLocks() == true` → `for update of c`). Ver
   Concurrency.
2. **Deadlock ABBA entre `Court` y `ClassSlot`** — descartada enumerando los nueve casos de uso que
   toman uno o ambos locks: ninguno toma `ClassSlot` antes que `Court`. Ver Concurrency.
3. **Discrepancia de zona horaria entre `LocalDateTime.now()` (dominio) y `LocalDateTime.now(clock)`
   (casos de uso)** — descartada: `ClockConfig` devuelve `Clock.systemDefaultZone()`, la misma zona que
   usa el `now()` implícito, y el Javadoc de `ClockConfig` documenta explícitamente que la ambigüedad
   de fondo es preexistente y deliberadamente no resuelta acá.
4. **`ClassRecurrenceAdapter.hasConflict` rechazando horarios legítimos por no filtrar día de la
   semana** — descartada: sí filtra, en Java (`.anyMatch(day -> day.getDayOfWeek() == slot.dayOfWeek())`),
   después de la query.
5. **`findByInstructorAndDateRange` como consulta sin cota superior** — descartada trazando al único
   llamador HTTP, que acota a 93 días. Ver Performance.
6. **Filas huérfanas en `court_occupancy` bloqueando una cancha para siempre** — descartada trazando
   el ciclo de vida completo y verificando que `DeleteCourtUseCase` no puede producirlas. Ver
   Database & Indexes.
7. **Error de conversión en `toMySQLDayOfWeek`** (`DAYOFWEEK()` de MySQL empieza en domingo) —
   descartada verificando la fórmula valor por valor: es correcta.
8. **README con la cifra de tests desactualizada** — descartada *para el estado commiteado*: 242
   anotaciones de test en HEAD contra las "239" que afirma el README. Pasará a ser deriva real recién
   cuando este branch se commitee.

## New Findings

Los seis hallazgos de esta pasada (**N1–N6**) son, todos, nuevos: ningún informe anterior los había
reportado. Vale explicitar de dónde salieron, porque no salieron de recorrer el checklist otra vez —
las seis pasadas anteriores ya lo recorrieron sobre este mismo código y no los encontraron.

**N1, N2, N3 y N4 salieron de invertir la dirección habitual de la comparación.** Lo natural, con una
rama que agrega un módulo nuevo grande, es auditar lo nuevo contra los estándares de lo viejo — que es
lo que hizo la auditoría del 09/09, correctamente, y así encontró F17. Esta pasada hizo lo contrario:
tomó las decisiones de validación que el módulo *nuevo* toma con cuidado evidente —el `@AssertTrue`
sobre los 72 bytes de BCrypt, el `StrictIntegerDeserializer`, los tipos `LocalTime`/`DayOfWeek` reales
en vez de strings, el filtro de "ya empezó" en tres lugares distintos— y fue a verificar, una por una,
si el código *viejo* las tiene. En cuatro casos no las tiene, y en tres de esos cuatro el código nuevo
prueba que el equipo conoce la trampa exacta que el viejo sigue pisando. Ese contraste es lo que
convierte a los cuatro en hallazgos accionables baratos en vez de recomendaciones genéricas: el
patrón correcto ya está escrito, en este repositorio, por la misma persona.

**N5 salió de aplicar al módulo nuevo la tabla de "una capa vs. dos capas" completa**, en vez de
mirarla solo donde ya había sospecha. El módulo de clases tiene cinco tablas nuevas y cuatro de ellas
llevan su `UNIQUE`; `class_slot` es la única que no, y es justamente la que sostiene el invariante más
caro. Que no haya carrera alcanzable hoy es precisamente lo que lo vuelve un hallazgo preventivo bien
planteado en vez de una alarma inflada — y la validación se usó para *confirmar* esa categoría
enumerando los tres escritores, no para degradarla.

**N6 salió del barrido lateral que la Fase 2 pide explícitamente**: aplicar a código adyacente el mismo
chequeo que produjo un hallazgo confirmado en otro lado. F9 (historial de reservas sin orden explícito)
es un hallazgo viejo; buscar la misma forma en los tres endpoints de lectura del módulo nuevo encontró
una reaparición en uno de ellos.

## Final Assessment

**Vara usada:** la misma que las seis auditorías previas — proyecto de portfolio/aprendizaje en etapa
temprana, instancia única en Railway, sin tráfico de producción real, y con el módulo de clases todavía
sin commitear. Con esa vara, la severidad práctica inmediata de todo lo encontrado es menor de lo que
los emojis sugieren por sí solos: hay tiempo de cerrar todo esto antes de que llegue a producción.

**Cero hallazgos Críticos, por séptima vez consecutiva.** Y conviene decir por qué eso no es rutina: la
disciplina de concurrencia de este proyecto —lock pesimista como primera lectura de la transacción,
`Court` antes que `ClassSlot`, sin un solo ciclo de locks en nueve casos de uso, con tests de
concurrencia contra MySQL real en vez de H2— está por encima de lo que se ve habitualmente en
proyectos de este tamaño, y esta pasada la volvió a poner a prueba (incluida una hipótesis de
serialización global que hizo falta desensamblar Hibernate para descartar) sin encontrarle una grieta.
Las cinco tablas nuevas están correctamente indexadas para cada consulta viva, sin N+1, sin deriva
entre entidad y migración, y con constraint de base en cuatro de cinco invariantes.

**Lo que esta auditoría cambia respecto al 09/09** no es el panorama de concurrencia ni el de base de
datos, que se sostienen. Es la **validación de entrada del código anterior al módulo de clases**, que
nadie había mirado con esta lente y que tiene tres agujeros Medium confirmados —dos de ellos
terminando en HTTP 500 sobre entradas de usuario perfectamente razonables, uno de ellos en un endpoint
público sin autenticar—. El detalle que lo hace fácil de aceptar y de arreglar: en los tres casos, el
código escrito más recientemente ya hace lo correcto.

**Qué priorizar primero, en orden:**

1. **N1** — es el único defecto que un usuario anónimo puede alcanzar hoy sin credenciales, y el
   arreglo son dos anotaciones copiadas de un archivo que ya está en el repositorio
   (`InstructorRequestDto`). Minutos de trabajo, riesgo cero.
2. **F17** — sigue siendo el único hallazgo Alto/Confirmado abierto, y sigue sin estar en producción.
   La corrección más barata (atar `ClassSlot.startTime`/`duration` a la grilla de `Schedule` de la
   cancha) es un cambio de validación, no una migración de datos.
3. **N2 y N3** — los dos Medium restantes. N2 tiene además una mitad trivial (agregar dos clases de
   excepción a `handleMalformedRequest`) que vale la pena hacer aunque el tipado del DTO se posponga,
   porque cubre también cualquier parseo futuro.
4. **F1** — confirmar empíricamente la topología de Railway y resolver el rate limiter. Sin cambios
   desde el 31/08: sigue siendo el ítem que más depende de un hecho externo no verificable desde el
   repositorio.
5. **F3 y la parte de F5 sobre `sport.name_sport`** — los dos constraints faltantes cuyo chequeo de
   duplicado se rompe después de la primera carrera. Sin cambios desde el 31/08.
6. **N5** — antes de escribir el primer `UpdateClassSlotUseCase`, no después. Si además se decide
   resolver F17 restringiendo `startTime` a la grilla, conviene hacer ambos en la misma migración.
7. **F4** — replicar a `UpdateScheduleUseCase`/`DeleteScheduleUseCase` el lock que `AddScheduleUseCase`
   ya tiene. Cambio mecánico, patrón probado en tres lugares distintos del código.
8. **N4, N6, F18, F19** — cambios de minutos cada uno, sin apuro.
9. **El resto** (F2, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, y la parte Low de F5) — mejoras
   genuinas de bajo riesgo si se posponen, sin cambios de orden respecto al 09/09.

**Antes de commitear este branch**, dos ítems de checklist que no son hallazgos pero que van a serlo si
se olvidan: actualizar la cifra de tests del README (línea 265: dice 239, el working tree tiene ~454
entre trackeados y no trackeados), y revisar los dos Javadocs de F19, que van a quedar publicados
describiendo como pendiente algo que el mismo commit entrega.

**Lo que esta revisión no pudo verificar:**

- **Las 14 clases de test que usan Testcontainers no ejecutaron ni una aserción** en esta sesión, por
  falta de Docker. Son precisamente las que cubren no-doble-reserva bajo concurrencia real, el `UNIQUE`
  de V2, el rollback de `CreateClassSessionUseCase`, la migración de Flyway contra MySQL y la carrera
  de F16. Los 426 tests que sí corrieron pasan todos, pero **esta auditoría no pudo confirmar en esta
  sesión que las garantías de concurrencia sigan verdes** — solo que el código que las implementa está,
  por lectura, donde debe estar. La auditoría del 09/09 sí las corrió (con 1 sola clase fallando por el
  mismo motivo) y estaban verdes sobre este mismo código.
- Volumen real de datos de cualquier tabla. Ninguna recomendación de índice de este informe depende de
  ese dato, precisamente por eso.
- Plan de ejecución real de ninguna consulta: no se corrió `EXPLAIN` contra ninguna base (el Skill lo
  prohíbe explícitamente). Las conclusiones de cobertura de índices son estructurales, por regla de
  prefijo izquierdo, no medidas.
- Topología de red real de Railway, más allá de lo que ya confirmó el loop de F1.
- Si existen hoy filas duplicadas en `owner_club`, `owners.dni` o `sport.name_sport` en la base
  desplegada.
- Si algún cliente frontend ya filtra por su cuenta los turnos pasados que devuelve el endpoint de
  disponibilidad (**N3**) — el repositorio es solo el backend. Eso cambiaría el impacto percibido por
  el usuario final, pero no el hecho de que el contrato del endpoint no es cierto.
