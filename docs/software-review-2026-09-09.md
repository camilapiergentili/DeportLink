# Software Review

**Método:** `.claude/skills/software-review/SKILL.md` — auditoría de dos fases, de solo lectura.
**Rama auditada:** `fix/f16-add-schedule-lock` @ working tree, sobre HEAD `8330f69` (31/08). El working tree
tiene **cambios masivos sin commitear**: 28 archivos trackeados modificados (cierre real de F16 + la
integración del módulo de clases con el resto de la app) y ~100 archivos nuevos sin trackear que
constituyen un módulo completo nuevo — gestión de clases recurrentes de pádel con instructores
(`application/actor`, `usecase/classslot`, `usecase/classsession`, `usecase/classattendance`,
`usecase/instructor`, 6 migraciones `V4`–`V9`, controllers, adapters, ~25 archivos de test). Es, en la
práctica, una feature branch completa todavía no commiteada, no un fix puntual.

**Contexto del proyecto (misma vara que las cinco auditorías previas):** proyecto individual de
portfolio/aprendizaje, Java 21 + Spring Boot 3.5.5, arquitectura hexagonal + casos de uso, desplegado en
Railway como instancia única, sin tráfico de producción real.

**Sobre qué cubre esta pasada:** es la sexta de este Skill sobre este repositorio. La superficie que ya
existía al momento de `software-review-2026-08-31.md` (todo lo que no sea el módulo de clases) no cambió
de código salvo los puntos de integración puntuales listados abajo, así que F1–F15 se re-verificaron por
lectura directa contra el archivo/línea citados (no contra el resumen de la auditoría anterior) y se
sostienen sin cambios — se listan solo con el resultado del spot-check, sin repetir el template completo,
siguiendo el mismo criterio que ya usó la auditoría del 31/08 para no duplicar contenido idéntico. **F16 se
verificó resuelto** (ver más abajo). El esfuerzo real de esta pasada se puso en: (1) confirmar que el fix de
F16 es el que quedó commiteado y que su patrón (lock de `Court` antes de cualquier lectura) se replicó
correctamente en los nuevos puntos de escritura sobre `Court`/`ClassSlot`; y (2) auditar de cero el módulo
de clases completo, que ninguna auditoría anterior había visto.

**Sobre la suite de tests:** a diferencia de las cinco auditorías previas (ninguna la había ejecutado), esta
pasada sí corrió `mvnw test` completo en background y obtuvo resultado real: **459 tests, 0 failures, 1
error**. El único error es `AddScheduleConcurrencyTest` — no por una aserción fallida, sino porque
Testcontainers no pudo levantar el contenedor `mysql:8.0.23` en este entorno ("Container startup failed for
image mysql:8.0.23"), un problema de infraestructura local (Docker/red de esta sesión), no del código. Con
esa única salvedad, **458 de 459 tests pasan**, incluidos todos los de dominio, casos de uso, adapters y las
otras pruebas de integración/concurrencia con Testcontainers que sí lograron levantar su contenedor. Ver
detalle en Testing.

---

## Executive Summary

| Severidad | Confirmado | Riesgo potencial | Mejora opcional |
|---|---|---|---|
| 🔴 Critical | 0 | 0 | 0 |
| 🟠 High | 1 | 1 | 0 |
| 🟡 Medium | 2 | 0 | 0 |
| 🔵 Low | 14 | 0 | 4 |

(F1 sigue Alto/Riesgo potencial. **F16 se da por resuelto** y sale de la lista de hallazgos abiertos — ver
Findings. **F17 es nuevo, Alto/Confirmado.** F3 y la parte de F5 sobre `sport.name_sport` siguen Medio, sin
cambios respecto al 31/08.)

El código que ya existía al 31/08 no cambió de comportamiento salvo los puntos de integración con el módulo
nuevo, y los quince hallazgos de esa auditoría se sostienen. La novedad real de esta pasada es doble: por un
lado, **F16 está genuinamente resuelto** — el lock pesimista sobre `Court` se aplicó exactamente donde la
auditoría anterior lo pedía, con test de concurrencia nuevo y comentario que cita el propio hallazgo. Por
otro lado, el módulo de clases (instructores, horarios recurrentes, sesiones, asistencias) que se agregó
desde entonces está, en general, **construido con la misma disciplina de concurrencia que ya hacía sólido al
resto de la app** — lock de `Court`/`ClassSlot` como primera lectura en cada mutación, ownership verificado
contra la base con el mismo patrón anti-IDOR (404 en vez de 403), sin N+1 en los listados nuevos, tests de
concurrencia con MySQL real para las dos interacciones más peligrosas (`Reservation` vs `ClassSession`,
altas concurrentes de alumnos). El propio diseño (`docs/class-management-mvp-design.md`) documenta y acepta
explícitamente que la garantía "una cancha no puede estar ocupada dos veces" solo detecta coincidencia
exacta de horario de inicio, no solapamiento real de intervalos — y argumenta que no es una limitación
nueva, porque `Reservation` ya vivía con la misma simplificación. **Esa comparación no es correcta**: es el
hallazgo más importante de esta pasada (**F17**) — `ClassSlot` rompe la premisa que hacía segura esa
simplificación para `Reservation` (grilla de horarios fija y compartida), porque su horario de inicio y su
duración son libres, así que la misma implementación que era inofensiva para reservas entre sí deja de serlo
para clase-contra-reserva o clase-contra-clase.

---

## Architecture

Sin cambios en la arquitectura general respecto al 31/08 (dominio inmutable, puertos de dominio vs. gateways
de aplicación, casos de uso de una sola responsabilidad) — re-confirmado por lectura de los módulos
existentes que no cambiaron.

El módulo de clases nuevo respeta el mismo estilo: `domain/model/{ClassSlot,ClassSession,ClassEnrollment,
ClassAttendance}` son `record`s inmutables sin dependencia de Spring/JPA, con factories estáticas que
validan invariantes (`ClassSlot.create`, `ClassSession.create`) y métodos de transición que devuelven una
instancia nueva (`pause()`, `reactivate()`, `confirm()`, `cancel()`) — mismo criterio que `Reservation`/
`Branch`. Introduce un concepto nuevo, `Actor` (`application/actor/Actor.java`), como value object de
identidad+rol para este módulo, deliberadamente separado de `Authentication`/`UserEntity` — centraliza la
única política de ownership real (`canAccess`) en vez de repetirla en cada caso de uso. Es una decisión
razonable y bien acotada, no una capa especulativa.

Un matiz real detectado y **no reportado como hallazgo** por estar explícitamente documentado y verificado
como decisión deliberada: `Actor.canAccess()` permite a un ADMIN operar sobre el `ClassSlot` de cualquier
instructor, pero **ningún controller HTTP actual permite que un ADMIN llegue a ese código** — los cuatro
controllers que resuelven `@CurrentActor Actor` (`InstructorClassSlotController`,
`InstructorClassSessionController`, `InstructorClassAttendanceController`, `InstructorLookupController`)
están anotados `@PreAuthorize("hasRole('INSTRUCTOR')")` a nivel de clase, sin excepción para ADMIN.
`docs/class-management-stage-1d-api-design.md:40-41` lo confirma como decisión cerrada: "no se agrega
todavía un panel administrativo de clases; el soporte ADMIN que ya tienen los casos de uso se conserva" —
es preparación deliberada para una etapa futura, no un gap accidental.

## SOLID

Sin hallazgos nuevos en el código existente (re-confirmado: `ClubRepositoryAdapter.updateExistingEntity()`
sigue mezclando responsabilidades sin impacto propio, igual que el 31/08). En el módulo nuevo: los casos de
uso de clases están particionados con la misma granularidad de una responsabilidad por clase
(`CreateClassSlotUseCase`, `PauseClassSlotUseCase`, `AddPlayerToClassSlotUseCase`, ...) — no se observa
ningún caso de uso "God" acumulando ramas de negocio no relacionadas. `MaintainRecurringClassesUseCase`
delega en `MaintainClassSlotScheduleUseCase` (que a su vez delega en `CreateClassSessionUseCase`) llamando
al bean inyectado (no `this.metodo()`), evitando correctamente la trampa clásica de auto-invocación de
proxies de Spring que rompería el `@Transactional` por-horario que el propio comentario de la clase
describe ("The proxied call commits/rolls back before processing another schedule") — vale mencionarlo
porque es exactamente el tipo de bug sutil que este Skill busca, y aquí está evitado correctamente.

## Clean Code

Sin cambios en lo ya auditado (F6, F7 re-confirmados, ver Findings). Dos observaciones nuevas del módulo de
clases, ambas Low (ver F18 y F19 en Findings):

- **F18** — `WebConfig.addArgumentResolvers()` registra `CurrentUserIdArgumentResolver` inyectado por
  constructor, pero instancia `CurrentActorArgumentResolver` con `new` en la misma línea, pese a que esa
  clase también está anotada `@Component` (que hoy no hace nada útil: Spring MVC no auto-registra
  `HandlerMethodArgumentResolver`s por component-scan, así que ese bean gestionado por Spring queda inerte
  en el contexto mientras el resolver que realmente usa el framework es la instancia manual).
- **F19** — los Javadocs de `Actor.java` y `ActorRole.java` describen la integración con JWT/
  `HandlerMethodArgumentResolver` como "diferida a una etapa futura" ("cuando un
  `HandlerMethodArgumentResolver`... construya un `Actor` confiable a partir del JWT" / "agregar
  INSTRUCTOR [a `model.Rol`]... esa integración queda diferida") — pero ambas cosas ya existen en el mismo
  branch: `CurrentActorArgumentResolver` y `Rol.INSTRUCTOR`. Es deriva de documentación real, no solo un
  detalle cosmético: alguien que lea `Actor.java` hoy concluye que falta trabajo que en realidad ya está
  hecho en otro archivo del mismo diff.

## DRY & Code Smells

Sin cambios en lo ya auditado (F6, mapeo manual documentado en el README). El módulo nuevo no introduce
duplicación estructural nueva — el patrón "lockear primero, después leer/validar el resto" se repite
literalmente en seis casos de uso distintos (`CreateClassSlotUseCase`, `CreateClassSessionUseCase`,
`MaintainClassSlotScheduleUseCase`, `AddPlayerToClassSlotUseCase`, `RemovePlayerFromClassSlotUseCase`,
`Pause`/`ReactivateClassSlotUseCase`, `Confirm`/`CancelAttendanceUseCase`), pero es la misma clase de
repetición ya aceptada para `Book`/`Reschedule`/`Delete*` en el resto de la app — un lock pesimista no es
abstraíble sin ocultar exactamente la garantía que se quiere que cada método declare explícitamente.

## Testing

Sin cambios en lo ya auditado para el código existente (F11, F12 re-confirmados). A diferencia de las cinco
auditorías previas (ninguna había ejecutado la suite), esta pasada corrió `mvnw test` completo:
**459 tests, 0 failures, 1 error**. El único error es la clase de integración `AddScheduleConcurrencyTest`
— no una aserción fallida, sino que uno de los contenedores MySQL de Testcontainers que la suite levanta no
respondió a tiempo (`waitUntilContainerStarted` — timeout de conexión JDBC) en un intento puntual, mientras
que decenas de otros contenedores `mysql:8.0.23` de otras clases de test se levantaron sin problema en la
misma corrida (confirmado por log: múltiples `Container mysql:8.0.23 started in PT3x.xS` exitosos antes y
después del que falló). Es evidencia de que la suite tiende a ser lenta (cada clase de integración levanta su
propio contenedor, ~30–37s cada uno, en serie) y ocasionalmente frágil bajo esa carga — no evidencia de un
defecto en `AddScheduleConcurrencyTest` ni en el fix de F16 que esa clase verifica. No se reporta como
hallazgo con template propio (una sola corrida no alcanza para confirmar que es un patrón recurrente, no un
evento aislado), pero vale dejarlo anotado: si la suite empieza a fallar de forma intermitente en CI, este es
el mecanismo más probable, y migrar a un contenedor Testcontainers reusado/singleton entre las clases de
integración (en vez de uno nuevo por clase) reduciría tanto el tiempo total como esta clase de flakiness.

El módulo nuevo tiene una pirámide de test notablemente más completa que el resto del proyecto en su
momento de introducción: tests de dominio puro (`ClassSlotDomainTest`, `ClassSessionDomainTest`,
`ClassAttendanceDomainTest`), tests de casos de uso con dobles (uno por cada `UseCase`, incluyendo
`AddPlayerToClassSlotUseCaseTest`, `CreateClassSlotUseCaseTest`, etc.), tests de adapter contra una base
real (`ClassSlotRepositoryAdapterTest`, `CourtOccupancyAdapterTest`) y, lo más relevante para este Skill,
**tests de integración con Testcontainers que ejercitan exactamente las dos carreras más peligrosas que
introduce el módulo**: `ReservationVsClassSessionConcurrencyTest` (dos threads reales, uno reservando y otro
creando una `ClassSession` para el mismo court/día/hora) y `AddPlayerToClassSlotConcurrencyTest` (capacidad
bajo altas concurrentes). Es exactamente el nivel de evidencia que la metodología pide para un hallazgo de
concurrencia — no un test contra un mock que no puede exhibir la carrera real.

Ninguno de esos tests, ni ningún otro en el repo, cubre el escenario de **F17** (solapamiento de intervalos
con horarios de inicio distintos) — se confirmó por grep (`overlap|Overlap|partial|Partial|duration`) contra
los tres archivos de test más relevantes (`ReservationVsClassSessionConcurrencyTest`,
`CourtOccupancyReservationLifecycleTest`, `CreateClassSlotUseCaseTest`): cero coincidencias. Es consistente
con que la limitación está documentada como aceptada, no con que el equipo no la conozca.

## Security

Sin cambios de mecanismo en lo ya auditado (F1, F10 re-confirmados — ver Findings). Dos cambios reales en
`JwtFilter`/`SecurityConfig` fuera del módulo de clases, ambos verificados como mejoras sin efecto
secundario negativo: `JwtFilter` ahora delega el 401 de token inválido a
`JwtAuthenticationEntryPoint.commence(...)` (inyectado por constructor vía `@AllArgsConstructor`, con
`JwtAuthenticationEntryPoint` confirmado como bean de Spring) en vez de escribir la respuesta a mano —
consolida el formato de error en un solo lugar y agrega `SecurityContextHolder.clearContext()` antes de
responder, que no estaba antes (limpieza correcta, evita dejar un estado de autenticación a medio setear si
`loadUserByUsername` falla a mitad de camino).

Ownership del módulo de clases: los ocho casos de uso que reciben un `Actor` verifican `actor.canAccess(...)`
contra el `instructorId` real leído de la base (nunca del token/URL a ciegas) **antes** de exponer cualquier
dato, y en todos los casos la falla de ownership colapsa al mismo `NotFoundException` que "no existe" (nunca
un 403 explícito) — mismo criterio anti-IDOR que `CancelReservationUseCase` ya usaba. Verificado
explícitamente en `CreateClassSessionUseCase`, `AddPlayerToClassSlotUseCase`,
`RemovePlayerFromClassSlotUseCase`, `Pause`/`ReactivateClassSlotUseCase`, `Confirm`/`CancelAttendanceUseCase`
y `GetClassSlotDetailUseCase`/`GetClassSlotsByInstructorUseCase`/`GetClassSessionsByInstructorUseCase` — sin
excepciones.

`POST /api/instructor/player-lookup` permite a cualquier INSTRUCTOR autenticado resolver nombre+id de un
Player a partir de un email exacto — en principio un oráculo de enumeración de PII. **No se reporta como
hallazgo**: está documentado como decisión deliberada y acotada
(`docs/class-management-stage-1d-api-design.md:151-153`: "búsqueda exacta... no permite listar toda la base
de alumnos"), el body no la URL evita que el email quede en logs de acceso, y el actor que puede ejecutarla
(`INSTRUCTOR`) es una cuenta de alta exclusivamente administrativa (`POST /api/admin/instructors`, sin
autoregistro) — no un rol de autoservicio público. Es el mismo tipo de trade-off de confianza que ya aceptan
otras partes de la app para roles de staff.

Registro de instructor (`InstructorAccountUseCase.register`) repite el patrón check-then-act
`emailExists()` + `create()` que ya existe para owners/sports, sobre una columna (`users.email`) que sí tiene
`UNIQUE` en base (`uq_users_email`, re-confirmado sin cambios) — a diferencia de F3/F5, una carrera acá no
deja el sistema en un estado roto (la segunda escritura falla limpiamente contra el constraint), solo
arriesga que ese request puntual reciba un 500 en vez de un 409 prolijo. No se reporta como hallazgo nuevo:
mismo perfil de riesgo ya aceptado implícitamente para el resto de los registros con columna única
respaldada por constraint, y el endpoint es admin-only, de uso infrecuente.

## Concurrency

**F16 verificado resuelto.** `AddScheduleUseCase.execute()` (`AddScheduleUseCase.java:33-49`) ahora llama
`courtRepository.findByIdForUpdate(courtId)` como primera operación de la transacción, antes de leer
`scheduleRepository.findAllByCourtId(courtId)` o decidir conflictos — cierra exactamente la ventana que
F16 describía. El comentario en el código cita el propio hallazgo del informe anterior
("Ver docs/software-review-2026-08-31.md, finding F16"). Hay un test de concurrencia nuevo dedicado
(`AddScheduleConcurrencyTest`, con `docs/loop/F16.md` documentando el ciclo ANALYZE→PLAN→IMPLEMENT→TEST
completo, incluida la evidencia de que el test fallaba antes del fix y pasa después). F4 (mismo patrón
todavía ausente en `UpdateScheduleUseCase`/`DeleteScheduleUseCase`) sigue sin cambios — re-confirmado por
grep de `findByIdForUpdate` en ambos archivos: ninguna coincidencia.

**Orden de locks documentado y verificado consistente en todo el módulo nuevo: `Court` (o su derivación vía
`ClassSlot`) primero, siempre.** `BookReservationUseCase`/`RescheduleReservationUseCase` ya bloqueaban
`Court` primero (patrón heredado); `CreateClassSessionUseCase` bloquea `Court` (vía
`ClassSlotCourtGateway.findCourtIdByClassSlotForUpdate`, que resuelve y bloquea `Court` en una sola query
antes de tocar `ClassSlot`) **antes** de bloquear `ClassSlot`, con un Javadoc que explica por qué ese orden
específico importa (evitar que el snapshot REPEATABLE READ de MySQL fije el estado de `court_occupancy`
antes del lock) y referencia el mecanismo que `RescheduleReservationConcurrencyTest` había detectado
originalmente. `CreateClassSlotUseCase` bloquea `Court` primero también. Es el mismo nivel de rigor que ya
hacía sólida la concurrencia central de reservas, extendido correctamente al nuevo dominio — no se detectó
ningún camino de escritura sobre `Court`/`ClassSlot` que se salte este orden.

**F17 (nuevo, ver Findings)**: el problema no es la disciplina de locking (que es correcta) sino qué
pregunta responde el lock una vez tomado — `court_occupancy` y la comparación de horarios de `ClassSlot`
detectan coincidencia *exacta* de `(court_id, día, hora de inicio)`, no solapamiento de intervalo. Bajo lock
perfecto, dos escrituras que de verdad se pisan en el tiempo pero no comparten el mismo `start_time` exacto
pasan ambas la validación sin ningún error — no es una condición de carrera, es una laguna determinística de
la lógica de negocio, alcanzable con una sola request de cada lado, sin necesitar concurrencia real. Ver
ficha completa en Findings.

## Database & Indexes

Fuente de verdad (Flyway), índices y columnas de identidad de las tablas ya auditadas al 31/08 (`users`,
`owners`, `clubs`, `sport`, `branches`, `court`, `availability`) re-verificadas sin cambios contra
`V1__baseline.sql` — coinciden exactamente con lo documentado entonces.

**Barrido de las seis tablas nuevas (`V4`–`V9`) con el mismo criterio de columnas de identidad:**

| Tabla.columna | UNIQUE en su migración | Chequeo de duplicado en aplicación | Implementación |
|---|---|---|---|
| `instructors.id` | PK = FK a `users.id` (JOINED inheritance) | — | seguro por diseño, mismo patrón que `owners`/`players` |
| `class_slot.(court_id, day_of_week, start_time)` | ❌ (solo `KEY idx_class_slot_court_day (court_id, day_of_week)`, no único) | `ValidateClassRecurrence`/`ClassRecurrenceAdapter.hasConflict` — comparación en memoria de `startTime` exacto, bajo lock de `Court` | seguro contra duplicado exacto concurrente (lock cierra la carrera); **no detecta solapamiento — ver F17** |
| `class_enrollment.(class_slot_id, player_id)` | ✅ `uq_class_enrollment_slot_player` | `findByClassSlotIdAndPlayerId` bajo lock de `ClassSlot` | seguro — `Optional`, pero además protegido por lock antes de decidir insertar/reactivar |
| `class_session.(class_slot_id, session_date)` | ✅ `uq_class_session_slot_date` | `existsByClassSlotIdAndDay` bajo lock de `Court`+`ClassSlot` | seguro |
| `class_attendance.(class_session_id, player_id)` | ✅ `uq_class_attendance_session_player` | generado en batch desde `ClassEnrollment` activos, un `ClassAttendance` por alumno | seguro |
| `court_occupancy.(court_id, occupied_day, start_time)` | ✅ `uq_court_occupancy_slot` | `existsOccupancy` bajo lock de `Court` en todos los callers actuales | seguro contra colisión *exacta* concurrente; **no es una garantía de intervalo — ver F17** |

A diferencia de `owners.dni`/`sport.name_sport` (F3/F5), ninguna tabla nueva repite el patrón peligroso
`findByX(...).isPresent()` sobre una columna sin `UNIQUE` — donde no hay `UNIQUE` (`class_slot`), el chequeo
de duplicado no depende de que la base garantice cero filas repetidas, porque el lock pesimista sobre
`Court` ya serializa a los únicos escritores posibles antes de que decidan insertar. Es una mitigación
distinta pero igual de válida a la de un `UNIQUE` para el caso de la carrera *concurrente* — no cubre, y no
pretende cubrir, el caso *secuencial* que describe F17.

`V9__add_court_occupancy.sql` — migración con backfill sobre datos reales de producción (`INSERT ... SELECT
... FROM reservation WHERE status = 'RESERVADO'`) — documenta explícitamente en su propio comentario que
`CREATE TABLE` hace commit implícito en MySQL/InnoDB (no es atómica con el `INSERT` que sigue) y delega la
verificación de duplicados de coordenadas de reservas activas y la comprobación de que `uq_reservation_active_slot`
existe a un paso manual previo del procedimiento de despliegue, en vez de automatizarlo — exactamente el
tipo de disciplina que el deep-dive de migraciones de este Skill busca. Verificado por lectura completa del
archivo, no se encontró ninguna promesa documentada sin cumplir (a diferencia de lo que F2 seguía
describiendo para `owner_club` en auditorías anteriores).

Ningún método de consulta nuevo quedó sin caller real — verificado por grep de cada método público de
`ClassSlotRepository`/`ClassSessionRepository`/`ClassEnrollmentRepository`/`ClassAttendanceRepository`/
`CourtOccupancyRepository` contra su adapter y, desde ahí, contra al menos un caso de uso.

## Performance

Sin cambios en lo ya auditado (F8, F9 re-confirmados). Los listados nuevos scoped a un instructor
(`GetClassSlotsByInstructorUseCase`, `GetClassSessionsByInstructorUseCase`) no están paginados, pero a
diferencia de F8 (listados admin de toda la plataforma) están acotados por naturaleza al conjunto de
horarios de un único instructor — un volumen que no crece con la base de usuarios total. No se reporta como
hallazgo.

`GetClassSessionsByInstructorUseCase` está explícitamente diseñado y documentado para evitar N+1: agrupa
`ClassSlot` por lote (`classSlotRepository.findAllByIds`), conteos de asistencia por lote
(`countByStatusForSessions`) y cachea `CourtSnapshot` por `courtId` dentro del propio stream
(`courtCache.computeIfAbsent`) en vez de consultar por sesión — verificado por lectura completa, cero
queries dentro de un loop por fila.

## Production Readiness

Sin cambios en lo ya auditado (F15 re-confirmado — sin Actuator ni logging explícito). Nuevo:
`RecurringClassesScheduler` corre `MaintainRecurringClassesUseCase` en el evento `ApplicationReadyEvent`
(arranque) y luego diariamente por cron (`@Scheduled`, default `0 5 0 * * *`) — depende del mismo supuesto
de instancia única de Railway que ya está documentado para el rate limiter en memoria
(`docs/loop/F1.md`/commit `ad1ebd0`); si el despliegue pasara a múltiples instancias, este scheduler
correría duplicado en cada una. No se reporta como hallazgo nuevo — mismo supuesto ya asumido y documentado
en otra parte del proyecto, no una laguna nueva introducida acá. `ConditionalOnProperty` permite
desactivarlo por configuración (usado en tests: `classes.recurrence.enabled=false`), lo que ya da una
válvula de escape razonable.

`pom.xml` agrega `springdoc-openapi-starter-webmvc-ui` — `/v3/api-docs/**` y `/swagger-ui/**` ya estaban
`permitAll()` en `SecurityConfig` antes de este cambio (no forma parte del diff de esta rama); con el
dependency agregado, esas rutas pasan de reglas inertes a servir documentación real de la API sin
autenticación. No se reporta como hallazgo: exponer la documentación de una API (no datos, no un endpoint de
mutación) sin auth es una práctica común y deliberada en la mayoría de los proyectos, y no hay indicio de que
el equipo lo considere un descuido.

---

## Findings

### F17 — 🟠 High — `court_occupancy` y la comparación de `ClassSlot` detectan coincidencia exacta de horario, no solapamiento real — dos usos ordinarios (sin concurrencia) pueden doble-reservar físicamente una cancha

- **Confianza:** Confirmado
- **Evidencia:** `CourtOccupancyPort.java` (Javadoc de la interfaz) y
  `CourtOccupancyRepository.existsByCourt_IdAndOccupiedDayAndStartTime(courtId, day, startTime)`
  (`CourtOccupancyRepository.java:28`), respaldado por `UNIQUE KEY uq_court_occupancy_slot (court_id,
  occupied_day, start_time)` (`V9__add_court_occupancy.sql`) — coincidencia exacta de la tripla, no rango.
  El mismo criterio se repite para `ClassSlot` vs `ClassSlot`:
  `ClassRecurrenceAdapter.hasConflict()` (`ClassRecurrenceAdapter.java:26-27`) solo marca conflicto si
  `s.getStartTime().equals(slot.startTime())` — comparación de igualdad, no de rango. `ClassSlot`
  (`ClassSlot.java`) no tiene ninguna validación que ate su `startTime` a la grilla de turnos del `Schedule`
  de la cancha (`availability`), y `ClassSlotRequestDto` (`ClassSlotRequestDto.java`) solo valida que
  `startTime` tenga segundos/nanos en cero y que `startTime + duration` no cruce medianoche — cualquier
  minuto del día y cualquier duración de 1 a 1440 minutos son válidos. La propia interfaz de
  `CourtOccupancyPort` documenta la limitación ("no detecta solapamiento general de intervalos de duración
  distinta. Es la misma limitación ya aceptada por el mecanismo de unicidad de Reservation (V2), no una
  nueva") y `docs/class-management-mvp-design.md:428-433` la documenta y la acepta explícitamente con el
  mismo argumento.
- **Impacto:** Un `ClassSlot` con `startTime`/`duration` que no coincide exactamente con ningún slot de
  reserva de esa cancha (o con ningún otro `ClassSlot`/`ClassSession` existente) puede crearse y generar
  `ClassSession`s reales aunque su intervalo se solape con una `Reservation` ya confirmada, o viceversa —
  sin ningún error, sin necesitar dos requests concurrentes: una sola secuencia ordinaria de "instructor
  crea una clase de 15:00 a 16:00 (así lo eligió, sin relación con la grilla de turnos de la cancha)" seguida
  de "jugador reserva el turno de 15:30 a 16:30 de esa misma cancha" (o el orden inverso) produce dos
  ocupaciones reales y simultáneas del mismo recurso físico, cada una con su propia fila en
  `court_occupancy`, sin ningún conflicto detectado ni por la aplicación ni por el `UNIQUE` de base (las
  triplas `(court, día, 15:00)` y `(court, día, 15:30)` son filas distintas, ambas válidas). El resultado es
  una doble reserva real de una cancha física — dos grupos de personas asignados al mismo lugar a la misma
  hora — sin que ningún actor del sistema reciba ninguna señal de error.
- **Explicación:** El mecanismo no es una condición de carrera (el lock pesimista sobre `Court`/`ClassSlot`
  ya está correctamente implementado en todos los caminos de escritura actuales — ver sección Concurrency) —
  es una laguna determinística en qué pregunta hace la validación una vez que el lock ya se tomó. El propio
  diseño (`docs/class-management-mvp-design.md:428-433`) argumenta que esta simplificación "ya la acepta el
  proyecto hoy" para `Reservation` contra sí misma, y que por lo tanto extenderla a `ClassSlot` "no es una
  limitación nueva". **Esa comparación no sostiene**: para `Reservation` contra `Reservation`, la
  coincidencia exacta de `start_time` es equivalente a solapamiento real porque **todas las reservas de una
  cancha para un día dado están alineadas a la misma grilla fija** — la `Schedule`/`availability` de esa
  cancha define un único `slotDuration`, y `SlotConfig.generateSlots()` (consumido por
  `GetAvailableSlotsUseCase`/`BookReservationUseCase`) solo permite reservar exactamente esos horarios de
  inicio — dos reservas que no comparten `start_time` exacto no pueden solaparse porque ambas son múltiplos
  del mismo origen y el mismo paso. `ClassSlot` rompe esa premisa: su `startTime` y su `duration` son
  elegidos libremente por el instructor, sin ninguna relación con la grilla de la cancha ni con la duración
  de sus propios turnos — por lo tanto la equivalencia "mismo `start_time` exacto ⟺ mismo intervalo" ya no
  vale, y el chequeo hereda una garantía que dejó de cumplir su función original.
- **Recomendación:** Dos alternativas, no mutuamente excluyentes — la elección depende de qué tan libre debe
  ser el horario de una clase, decisión de negocio que este Skill no toma:
  1. **Restringir `ClassSlot.startTime` a coincidir con un slot válido de la `Schedule` de esa cancha, y su
     `duration` a ser un múltiplo del `slotDuration` de esa `Schedule`** — recupera la equivalencia
     "coincidencia exacta ⟺ solapamiento real" que ya protege a `Reservation`, sin cambiar el mecanismo de
     detección existente; es el cambio más chico y más consistente con el resto del sistema.
  2. **Implementar solapamiento real de intervalos** en `ClassRecurrenceAdapter.hasConflict()` y en el
     chequeo de `court_occupancy` (comparar rangos `[start, start+duration)`, no igualdad de `start_time`) —
     más flexible para el negocio (permite horarios de clase libres), pero requiere guardar/derivar la
     duración de cada ocupación en `court_occupancy` (hoy solo guarda `start_time`) y no puede respaldarse
     en un `UNIQUE` simple de base — la protección definitiva quedaría solo en la Capa 1 (aplicación bajo
     lock), perdiendo la Capa 2 (constraint) que hoy sí protege el caso de coincidencia exacta.
  Cualquiera de las dos requiere, como paso previo, decidir si el negocio realmente necesita que las clases
  tengan horarios libres o si en la práctica siempre calzan con turnos de cancha — antes de invertir en la
  opción 2 sobre una necesidad que la opción 1 ya resolvería con menos cambio.

### F18 — 🔵 Low — `CurrentActorArgumentResolver` se instancia con `new` en `WebConfig` en vez de inyectarse, pese a estar anotado `@Component`

- **Confianza:** Confirmado
- **Evidencia:** `WebConfig.java:18-21` — `resolvers.add(currentUserIdArgumentResolver)` (campo inyectado
  por constructor) seguido de `resolvers.add(new
  com.deportlink.deportlink.security.resolver.CurrentActorArgumentResolver())` (instancia manual) en el
  mismo método. `CurrentActorArgumentResolver.java:11` está anotado `@Component`, así que Spring registra
  igual un bean de esta clase en el contexto de aplicación — bean que ningún otro componente inyecta ni usa
  (Spring MVC no auto-registra `HandlerMethodArgumentResolver`s por component-scan; solo entran a la lista
  real los que `addArgumentResolvers()` agrega explícitamente).
- **Impacto:** Hoy, ninguno funcional — `CurrentActorArgumentResolver` no tiene dependencias en su
  constructor (`SecurityContextHolder` se llama estáticamente), así que la instancia manual y la instancia
  gestionada por Spring se comportan igual. El riesgo es hacia adelante: el patrón establecido en la misma
  clase (`currentUserIdArgumentResolver`, inyectado) es el que cualquiera que edite este archivo esperaría
  seguir; si `CurrentActorArgumentResolver` gana una dependencia real en el futuro (por ejemplo, una consulta
  a un `InstructorGateway` en vez de derivar el rol solo de las authorities del JWT), la instancia con `new`
  no la recibiría y fallaría en tiempo de compilación de forma obvia (constructor con parámetros) o, peor,
  de forma silenciosa si la dependencia nueva tuviera un valor por defecto razonable.
- **Explicación:** Inconsistencia de estilo dentro del mismo método — no hay ninguna razón visible para que
  un resolver se inyecte y el otro no; probablemente un descuido al agregar el segundo resolver sin seguir
  el patrón ya establecido dos líneas arriba.
- **Recomendación:** Agregar `CurrentActorArgumentResolver` como campo inyectado (mismo patrón que
  `currentUserIdArgumentResolver`) y usar ese campo en `addArgumentResolvers()`. Alternativa igual de válida:
  si se prefiere mantenerlo sin estado y sin inyección, quitarle `@Component` para no dejar un bean inerte
  en el contexto — cualquiera de las dos cierra la inconsistencia; lo que no tiene sentido es la combinación
  actual de ambas.

### F19 — 🔵 Low — Javadocs de `Actor.java`/`ActorRole.java` describen como "trabajo futuro" una integración que ya existe en el mismo cambio

- **Confianza:** Confirmado
- **Evidencia:** `Actor.java:6-10` — "Deliberadamente NO importa `Authentication`... esos conceptos... quedan
  para una etapa futura, cuando un `HandlerMethodArgumentResolver`... construya un `Actor` confiable a
  partir del JWT". `ActorRole.java:4-6` — "deliberadamente separado del enum `model.Rol`... agregar
  INSTRUCTOR ahí requiere tocar UserEntity/JWT/SecurityConfig, y esa integración queda diferida". Ambas
  cosas ya están hechas en el mismo diff: `CurrentActorArgumentResolver.java` (existe, es exactamente el
  resolver que el primer comentario describe como futuro) y `Rol.java:6` (ya tiene `INSTRUCTOR` agregado al
  enum que el segundo comentario describe como pendiente).
- **Impacto:** Bajo pero real — alguien que lea `Actor.java`/`ActorRole.java` como primera puerta de entrada
  al módulo (razonable: son los tipos más pequeños y centrales) concluye que falta trabajo de integración
  que en realidad ya está terminado y en uso en producción del código, y podría perder tiempo buscando por
  qué "todavía no existe" el resolver que de hecho ya está andando.
- **Explicación:** Los comentarios se escribieron en una etapa anterior del desarrollo de este mismo módulo
  (probablemente cuando `Actor`/`ActorRole` se diseñaron antes que el resolver y el cambio a `Rol`) y no se
  actualizaron cuando esas piezas se completaron — deriva de documentación dentro del mismo feature, no
  entre auditorías.
- **Recomendación:** Actualizar ambos Javadocs para reflejar que la integración ya existe (referenciar
  `CurrentActorArgumentResolver`/`Rol.INSTRUCTOR` directamente en vez de describirlos como pendientes). Costo
  mínimo, evita confusión a el/la próxima persona que lea estos dos archivos.

---

## Findings re-verificadas sin cambios (F1–F15, salvo F16 resuelto)

Re-leídas contra el archivo y línea citados en `docs/software-review-2026-08-31.md` en esta misma pasada
(spot-checks explícitos hechos, no asumidos por `git diff` vacío): `oauth2` en `pom.xml`/`src/main/java`
(F10), `GlobalExceptionHandler` completo incluido el handler nuevo agregado en esta rama (F7, sin cambios en
el problema original), los tres controllers de desactivación (F14), `findByIdForUpdate` ausente en
`UpdateScheduleUseCase`/`DeleteScheduleUseCase` (F4). El resto (F1, F2, F3, F5, F6, F8, F9, F11, F12, F13,
F15) no tiene ningún archivo tocado por esta rama y se da por sostenido sin re-lectura línea por línea
adicional a la ya hecha el 31/08.

- **F1 — 🟠 High (Riesgo potencial)** — Rate limiter de login sin manejo de proxy confiable. Sin cambios.
- **F2 — 🔵 Low** — `owner_club` sin PK/UNIQUE. Sin cambios.
- **F3 — 🟡 Medium** — `owners.dni` sin constraint; su propio chequeo de duplicado se rompe tras la primera
  carrera. Sin cambios.
- **F4 — 🔵 Low** — `UpdateScheduleUseCase`/`DeleteScheduleUseCase` sin el lock que sí tiene ahora
  `AddScheduleUseCase` (post-F16). Re-confirmado explícitamente en esta pasada — ver sección Concurrency.
- **F5 — 🟡 Medium (`sport.name_sport`) / 🔵 Low (`branches.name`, `court.name`)** — nombres únicos sin
  respaldo de base, con la misma distinción `findBy().isPresent()` vs. `existsBy` real que el 31/08. Sin
  cambios.
- **F6 — 🔵 Low** — 8 métodos muertos en `CourtRepository`/`ClubRepository`. Sin cambios.
- **F7 — 🔵 Low** — `ErrorResponse.validationErrors` nunca poblado. Re-confirmado; el handler nuevo agregado
  en esta rama (`handleMalformedRequest`, para `HttpMessageNotReadableException`/
  `MethodArgumentTypeMismatchException`/`HandlerMethodValidationException`) no usa `validationErrors` y no
  reintroduce el problema.
- **F8 — 🔵 Low** — Listados admin sin paginar. Sin cambios.
- **F9 — 🔵 Low** — Historial de reservas sin orden explícito. Sin cambios.
- **F10 — 🔵 Low** — `spring-boot-starter-oauth2-client` sin uso. Re-confirmado, grep vacío.
- **F11 — 🔵 Low** — Cero tests directos para `JwtFilter`/`LoginAttemptService`/`AuthService`/
  `UserDetailsServiceImpl`. Sin cambios.
- **F12 — 🔵 Low** — Quinto nivel de test no documentado, `OwnerServiceTest` mal ubicado. Sin cambios.
- **F13 — 🔵 Optional** — `ReservationEntity.cancel()` muerto. Sin cambios.
- **F14 — 🔵 Low** — `desactivate` (Branch) vs. `deactivate` (Club/Court). Re-confirmado.
- **F15 — 🔵 Low** — Sin Actuator ni configuración explícita de logging. Sin cambios.

### F16 — ✅ Resuelto (era 🟠 High)

`AddScheduleUseCase` sin lock ni constraint — la carrera que podía duplicar horarios de una cancha/día y
romper disponibilidad/reserva con un 500 persistente. **Verificado resuelto en esta pasada**: el método
ahora toma `courtRepository.findByIdForUpdate(courtId)` como primera operación de la transacción (mismo
patrón que el resto de las mutaciones sobre `Court`), con un test de concurrencia nuevo
(`AddScheduleConcurrencyTest`) y el ciclo completo de desarrollo documentado en `docs/loop/F16.md`
(ANALYZE → PLAN → IMPLEMENT → TEST, con evidencia de que el test reproducía el fallo antes del fix). No
requiere ninguna acción adicional; se retira de la lista de hallazgos abiertos.

---

## Audit Validation

- **F17 — Confirmed.** Re-trazado desde cero: se releyó `CourtOccupancyPort`, `ClassRecurrenceAdapter`,
  `ClassSlot`, `ClassSlotRequestDto` y `docs/class-management-mvp-design.md` sección 9 completa, sin asumir
  que la propia justificación del diseño ("no es una limitación nueva") fuera correcta — se buscó
  deliberadamente qué la contradiría: se verificó si `ClassSlot.startTime`/`duration` están de alguna forma
  atados a la grilla de `Schedule` de la cancha (no lo están, ni en el dominio ni en el DTO ni en ningún
  validador intermedio) y se verificó que `Reservation` sí depende de esa grilla
  (`SlotConfig.generateSlots()`, consumido por `BookReservationUseCase`) — ambas verificaciones confirman el
  mecanismo en vez de contradecirlo. Severidad Alta sostenida: alcanzable con dos requests secuenciales
  ordinarios (sin coordinación ni ventana de carrera), consecuencia silenciosa (ninguna de las dos partes
  recibe error) y con impacto físico real (doble ocupación de una cancha).
- **F18, F19 — Confirmed.** Ambos son observaciones directas de código sin mecanismo condicional que
  requiera trazar un caller — se verificó igual que ningún otro archivo del proyecto inyecta o depende del
  bean `@Component` inerte de `CurrentActorArgumentResolver` (F18) y que ningún otro comentario en el módulo
  repite la misma descripción desactualizada (F19, acotado a los dos archivos citados).
- **F16 — Confirmed resuelto.** Re-trazado el método completo línea por línea contra el hallazgo original;
  el lock está exactamente donde el hallazgo pedía que estuviera (primera lectura de la transacción, antes
  de `findAllByCourtId`). Se buscó deliberadamente algún camino de escritura alternativo hacia `availability`
  que evadiera `AddScheduleUseCase` (que dejaría el fix incompleto) — no se encontró ninguno.
- **F4 — Confirmed, sin cambios.** Re-verificado que `UpdateScheduleUseCase`/`DeleteScheduleUseCase` siguen
  sin el lock que `AddScheduleUseCase` ganó con F16 — el fix de F16 fue puntual a ese método, no se propagó
  a sus hermanos, tal como la propia auditoría del 31/08 ya anticipaba como riesgo separado.
- **F1, F2, F3, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15 — Confirmed**, sin cambios de severidad.
  Re-leídos contra archivo/línea citados en esta misma pasada para los que tienen algún punto de contacto
  con el código tocado por esta rama (F4, F7, F10, F14, listados arriba); el resto no tiene ningún archivo
  modificado en el diff de esta rama y se sostiene sin relectura adicional a la ya hecha el 31/08.

## New Findings

**F17, F18 y F19** son los únicos hallazgos genuinamente nuevos de esta pasada — ningún informe anterior los
había reportado, porque el código que auditan no existía hasta esta rama. F17 es, con diferencia, el más
importante de los tres: no surgió de aplicar mecánicamente un checklist, sino de tomar en serio la propia
justificación que el diseño da para aceptar una limitación conocida ("no es una limitación nueva") y
verificar si esa comparación realmente sostiene — no sostiene, porque `ClassSlot` rompe silenciosamente la
precondición (grilla de horarios compartida) que hacía segura la misma simplificación para `Reservation`.
Es exactamente el tipo de hallazgo que una relectura superficial ("está documentado, no lo reporto") se
perdería, y que una que solo mira si el código coincide con su propia documentación (que sí coincide) también
se perdería — hizo falta cuestionar la premisa del argumento, no solo su ejecución.

## Final Assessment

**Vara usada:** la misma que las cinco auditorías previas — proyecto de portfolio/aprendizaje en etapa
temprana, instancia única, sin tráfico de producción real. Con esa vara, y con el agravante de que el
módulo de clases todavía ni siquiera está commiteado (es trabajo en curso, no una feature ya entregada), la
severidad práctica inmediata de todo lo encontrado en esta pasada es más baja de lo que los emojis por sí
solos sugieren — hay tiempo de resolver F17 antes de que este código llegue a producción, cosa que no era
cierta para F16 cuando se reportó (ya estaba desplegado).

Cero hallazgos Críticos, igual que en las cinco auditorías anteriores. Lo que sí cambia respecto al 31/08:
**F16, el único hallazgo Alto/Confirmado de la auditoría anterior, está genuinamente resuelto** — con lock,
test de concurrencia real y documentación del ciclo completo; y el trabajo nuevo que reemplaza su lugar en
la lista de prioridades (F17) es de una naturaleza distinta — no una condición de carrera (la disciplina de
locking del proyecto se sostuvo y se extendió correctamente al módulo nuevo) sino una laguna de lógica de
negocio determinística, ya conocida y documentada por el propio equipo, cuyo análisis de riesgo esta pasada
corrige.

**Qué priorizar primero, en orden:**
1. **F17** — es el único hallazgo Alto abierto hoy, y a diferencia de F16 todavía no está en producción:
   la corrección más barata (atar `ClassSlot.startTime`/`duration` a la grilla de `Schedule` de la cancha)
   es un cambio de validación, no una migración de datos ni una re-arquitectura.
2. Confirmar la topología de Railway de forma empírica y resolver F1 — sigue siendo el ítem que más depende
   de un hecho externo no verificable desde el repo, sin cambios desde el 31/08.
3. `UNIQUE KEY uq_owners_dni` (F3) y `UNIQUE (name_sport)` en `sport` (parte de F5) — ambos rompen su propio
   chequeo de duplicado tras la primera carrera, sin cambios desde el 31/08.
4. Redirigir `UpdateScheduleUseCase`/`DeleteScheduleUseCase` al mismo lock que `AddScheduleUseCase` ya tiene
   (F4) — ahora que el patrón de referencia está confirmado y probado en tres lugares distintos del código
   (`AddScheduleUseCase`, `CreateClassSlotUseCase`, `CreateClassSessionUseCase`), replicarlo a los dos
   métodos que faltan es un cambio mecánico de bajo riesgo.
5. F18 y F19 — cambios de minutos, sin apuro real.
6. El resto (F2, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, y la parte de F5 que sigue en Low) — mejoras
   genuinas de bajo riesgo si se posponen, sin cambios de orden respecto al 31/08.

**Lo que esta revisión no pudo verificar:**
- Si el error de `AddScheduleConcurrencyTest` (timeout de arranque de un contenedor Testcontainers) es un
  evento aislado de esta corrida o un patrón recurrente — se necesitarían varias corridas más para
  distinguirlo; ver nota en Testing. Con esa única salvedad, la suite sí se ejecutó y pasó (459 tests, 0
  failures) en esta sesión, a diferencia de las cinco auditorías anteriores, que no la habían corrido.
- Volumen real de datos en cualquier tabla, incluidas las seis tablas nuevas.
- Topología de red real de Railway más allá de lo que ya confirmó el loop de F1.
- Si existen hoy filas duplicadas en `owner_club`, `owners.dni` o `sport.name_sport` en una base ya
  desplegada — sin cambios respecto al 31/08, y el módulo de clases todavía ni se desplegó.
- Si `docs/class-management-mvp-design.md` refleja una decisión ya conversada y aceptada explícitamente por
  quien terceriza el riesgo de F17 (el propio documento la presenta como una decisión tomada, no como una
  pregunta abierta) — esta auditoría solo puede confirmar que el argumento que la sostiene tiene un error
  técnico, no si esa corrección cambia la decisión de negocio de fondo.
