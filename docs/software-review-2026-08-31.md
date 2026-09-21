# Software Review

**Método:** `.claude/skills/software-review/SKILL.md` — auditoría de dos fases, de solo lectura.
**Rama/commit auditado:** `fix/f1-login-rate-limiter-proxy-ip` @ HEAD (`f1b0d2f`). `git diff 9a5e365 HEAD --stat`
muestra un único archivo tocado desde la última revisión completa: `docs/loop/F1.md` (agregado). **El
código de `src/` y `pom.xml` no cambió ni una línea** desde `docs/software-review-2026-08-30.md`.
**Contexto del proyecto (vara usada en todo el informe):** proyecto individual de portfolio/aprendizaje,
Java 21 + Spring Boot 3.5.5, arquitectura hexagonal + casos de uso, desplegado en Railway como instancia
única, sin tráfico de producción real — misma vara que las cuatro auditorías previas.

**Sobre re-derivar contra código idéntico:** este es el cuarto informe de este Skill sobre este repositorio
(`audit-2026-08-25.md`, `software-review-2026-08-26.md`, `audit-database-2026-08-26.md`,
`software-review-2026-08-30.md`), y el código no cambió desde el tercero. Releer y re-derivar los quince
hallazgos ya confirmados desde cero, palabra por palabra, no habría agregado nada — la instrucción del
propio Skill para este caso es explícita: re-verificar que sigan sosteniéndose y no reescribirlos con el
template completo si no cambian. Eso es lo que se hizo para F1, F6–F15 (ver Findings): releídos contra el
archivo citado, no contra la descripción de la auditoría anterior, y confirmados sin cambios. El esfuerzo
real de esta pasada se puso en dos lugares: (1) los dos hallazgos de identidad de la auditoría anterior
(F3, F5) resultaron tener una segunda consecuencia que esa pasada no había trazado — se investiga y se
suma abajo; y (2) una búsqueda deliberada de código estructuralmente idéntico a lo ya encontrado que
ninguna pasada anterior había mirado todavía, que encontró un hallazgo nuevo (F16) con mecanismo real y
más severo que los de la misma familia. También se incorpora la evidencia nueva que produjo el loop de
desarrollo sobre F1 (`docs/loop/F1.md`, iteraciones 1 y 2): confirma que Railway sí antepone un proxy, pero
dentro del propio soporte de Railway hay guía contradictoria sobre qué header y posición confiar — esto no
cambia el mecanismo de F1, pero sí cierra una de sus dos ramas posibles y afina su recomendación.

---

## Executive Summary

| Severidad | Confirmado | Riesgo potencial | Mejora opcional |
|---|---|---|---|
| 🔴 Critical | 0 | 0 | 0 |
| 🟠 High | 1 | 1 | 0 |
| 🟡 Medium | 3 | 0 | 0 |
| 🔵 Low | 12 | 0 | 4 |

(F1 cuenta como Alto/Riesgo potencial, igual que en la auditoría del 30/08; F16 es nuevo y se suma como
Alto/Confirmado — ver razonamiento de severidad en su ficha.)

El código no cambió desde la última auditoría completa, y los quince hallazgos de esa pasada (F1–F15) se
re-verificaron contra el código actual y se sostienen sin cambios de severidad, con dos excepciones que sí
se actualizan con evidencia nueva: **F3** (`owners.dni` sin constraint) y **F5** (nombres únicos sin
respaldo de base) tienen una segunda consecuencia que la pasada anterior no había trazado — el propio
chequeo `existsByDni`/`existsByNameIgnoreCase` que se supone previene el duplicado está implementado sobre
una query de resultado único (`Optional<Entity> findByX(...).isPresent()`), así que una vez que la
condición de carrera produce un duplicado, **ese mismo chequeo empieza a tirar un 500 en vez de detectar
correctamente "ya existe"** para cualquier intento futuro con ese mismo valor — no solo para el par de
filas concurrentes originales. Investigar esa misma forma de bug en otros lugares estructuralmente
similares llevó al hallazgo nuevo de esta pasada: **F16**, en `AddScheduleUseCase` — el mismo patrón de
carrera (chequeo de solapamiento en memoria + `saveAll`, sin lock ni constraint) puede insertar dos filas
de `availability` para la misma cancha/día, y a partir de ahí **toda consulta de horario disponible o de
reserva para esa cancha/día específica** (`GetAvailableSlotsUseCase`, `BookReservationUseCase`,
`RescheduleReservationUseCase`) rompe con un 500 hasta que alguien corrija el duplicado a mano en la base —
un efecto más severo que el que describía F4 (horario que queda "por fuera" silenciosamente), porque acá
el resultado es una interrupción visible del flujo de reserva de esa cancha, alcanzable con algo tan común
como un doble clic en el formulario de alta de horario.

---

## Architecture

Sin cambios respecto a la auditoría del 30/08 — código idéntico. Re-confirmado por lectura: dominio
inmutable (`domain/model/` son `record`s sin dependencia de Spring/JPA), separación real entre
`domain/port/out/` (puertos pesados, agregado completo) y `application/port/out/` (gateways de snapshot),
casos de uso de una sola responsabilidad, adapters con mapeo manual dominio↔entidad. La arquitectura
hexagonal completa es más maquinaria de la que un CRUD de este tamaño necesitaría por sí solo, pero el
proyecto es explícitamente de portfolio/aprendizaje y no se observa elaboración especulativa — no se
reporta como hallazgo, igual que en la pasada anterior.

## SOLID

Sin hallazgos nuevos. `ClubRepositoryAdapter.updateExistingEntity()` sigue mezclando reconciliación de
owners con actualización de campos (mismo método discutido en la nota de F2 de la auditoría anterior) —
no es un hallazgo con impacto propio.

## Clean Code

Sin cambios. Re-confirmado: `GlobalExceptionHandler.handleValidationException`
(`GlobalExceptionHandler.java`, método `handleValidationException`) construye `validationErrors` y solo lo
usa vía `.toString()` dentro de `message` — el campo `ErrorResponse.validationErrors` nunca se puebla (F7,
sin cambios). Comentario desactualizado en `ClubEntity.java:24` (sigue citando `findApprovedWithEagerLoading`
como si tuviera caller — re-verificado sin caller real, ver F6).

## DRY & Code Smells

Sin cambios. F6 (8 métodos de consulta muertos) re-verificado por conteo de referencias en dos puntos
concretos (`findApprovedWithEagerLoading`, `findActiveByCourtAndDay` del repositorio) — mismo resultado que
el 30/08. Mapeo manual duplicado en 4 adapters sigue siendo la misma decisión ya documentada en el README,
no un hallazgo.

## Testing

Sin cambios respecto al 30/08 — no se ejecutó la suite en esta sesión (mismo código, mismo estado de
`target/site/jacoco/` y `target/surefire-reports/` de la build anterior). F11 (cero tests directos para
`JwtFilter`/`LoginAttemptService`/`AuthService`/`UserDetailsServiceImpl`) y F12 (quinto nivel de test sin
documentar, `OwnerServiceTest` mal ubicado) re-verificados sin cambios.

Un punto nuevo relevante para F16 (ver Findings): tampoco hay ningún test de concurrencia para
`AddScheduleUseCase` — ni siquiera el estilo Testcontainers que sí existe para `Book`/`Reschedule`/
`DeleteCourt`/`DeleteBranch`. Es la misma laguna que la auditoría anterior ya notó para las mutaciones de
Schedule en general (F4), pero acá se confirma que tampoco hay cobertura para el escenario específico de
F16 (dos altas concurrentes de horario para la misma cancha).

## Security

Sin cambios de mecanismo respecto al 30/08, con una actualización de evidencia externa para F1 (ver
Findings): el loop de desarrollo (`docs/loop/F1.md`) confirmó con documentación oficial de Railway que sí
hay un proxy delante de la app, cerrando la rama "tal vez no hay proxy y el código ya funciona" que la
auditoría anterior había dejado abierta — pero encontró guía contradictoria del propio soporte de Railway
sobre qué header y qué posición son confiables, lo cual bloquea implementar una corrección seguramente
correcta hoy mismo (ver F1).

Re-verificado sin cambios: autorización por ownership contra la base en los tres beans
`@courtAuthorization`/`@branchAuthorization`/`@clubAuthorization`; IDOR 404-en-vez-de-403 en reservas;
validación de entrada en las 12 DTOs; `CORS_ALLOWED_ORIGINS` validado al arrancar; dependencia sin uso
`spring-boot-starter-oauth2-client` (F10, re-confirmado por grep vacío de `oauth2`/`OAuth2` en
`src/main/java`).

Chequeado explícitamente en esta pasada, no cubierto en el mismo nivel de detalle antes — **manejo de
sesión/token JWT**: `JwtUtil.generateToken()` firma con `Keys.hmacShaKeyFor(...)` (algoritmo derivado de la
fuerza de la clave, sin `alg: none` ni algoritmo débil hardcodeado), expiración de 10 horas
(`jwt.expiration=36000000` en `application.properties`), sin mecanismo de revocación/blacklist ni endpoint
de logout — un JWT emitido sigue siendo válido hasta que expira, incluso si el usuario es dado de baja o
cambia su contraseña después. **No se reporta como hallazgo**: es el trade-off estándar y esperado de un
JWT sin estado (revocarlo requeriría un store adicional, contradice el propósito de "sin estado"), no hay
ningún flujo de "dar de baja" o "cambiar contraseña" en el código actual que este gap pueda romper de forma
concreta, y el proyecto no lo documenta como garantía que no cumple. Se deja como una limitación conocida
del diseño, no como defecto. `JwtFilter.doFilterInternal()` resuelve las autoridades desde
`UserDetailsService.loadUserByUsername()` (consulta a la base en cada request), no desde el claim `role`
embebido en el token — confirmado correcto: un cambio de rol en la base aplica en el próximo request, no
haría falta esperar a que expire el token viejo.

## Concurrency

El patrón de referencia (`findByIdForUpdate` como primera operación, antes de cualquier chequeo de
disponibilidad) sigue confirmado en `Book`/`Reschedule`/`DeleteCourt`/`DeleteBranch`, probado con
Testcontainers — sin cambios.

F4 (mutaciones de Schedule sin el mismo lock; la query con lock existe pero no se llama) re-verificado
línea por línea, sin cambios.

**Nuevo en esta pasada (F16):** se investigó específicamente si el mismo patrón de carrera que F4 nombra
para `UpdateScheduleUseCase`/`DeleteScheduleUseCase` también aplica al tercer método que F4 menciona de
paso pero no evidencia con su propio mecanismo — `AddScheduleUseCase`. Sí aplica, y con una consecuencia
más severa y más fácil de alcanzar que la que F4 describe para los otros dos (ver ficha F16 completa en
Findings).

F2 (owner_club sin PK, no explotable hoy), F3 y F5 (identidad sin respaldo de base) re-verificados; F3 y F5
tienen evidencia nueva sobre una segunda consecuencia (ver Findings) que no cambia si la carrera original es
alcanzable, pero sí cambia qué pasa después de que ocurre una vez.

## Database & Indexes

Fuente de verdad (Flyway fuera de `dev`), índices por tabla y código muerto re-verificados sin cambios
contra `V1__baseline.sql` — coincide exactamente con lo documentado el 30/08.

**Barrido adicional hecho en esta pasada** sobre toda columna con semántica de identidad, incluyendo tablas
que la auditoría anterior no detalló en esta sección (`users`, `clubs`, `tickets`, `availability`,
`sport`):

| Tabla.columna | UNIQUE en `V1__baseline.sql` | Chequeo de duplicado en aplicación | Implementación del chequeo |
|---|---|---|---|
| `users.email` | ✅ `uq_users_email` | — (DB es la única fuente) | — |
| `owners.cuil` | ✅ `uq_owners_cuil` | `existsByCuil` | `findByCuil(...).isPresent()` — seguro, DB nunca deja duplicar |
| `owners.dni` | ❌ | `existsByDni` | `findByDni(...).isPresent()` — **inseguro, ver F3** |
| `clubs.cuit` | ✅ `uq_clubs_cuit` | `findByCuit` | seguro, DB nunca deja duplicar |
| `clubs.legal_name` | ✅ `uq_clubs_legal_name` | `findByLegalName` | seguro, DB nunca deja duplicar |
| `tickets.reservation_id` | ✅ `uq_tickets_reservation_id` | — | — |
| `sport.name_sport` | ❌ | `existsByNameIgnoreCase` | `findByNameSport(...).isPresent()` — **inseguro, ver F5** |
| `branches.(club_id, name)` | ❌ | `existsByNameIgnoreCaseAndClub` | `existsByNameIgnoreCaseAndClub_Id` (derived `existsBy` real, seguro pase lo que pase) |
| `court.(branch_id, sport_id, name)` | ❌ | `existsByNameAndBranchAndSport` | `existsByNameAndBranch_IdAndSport_Id` (derived `existsBy` real, seguro) |
| `availability.(court_id, day_of_week)` | ❌ | ninguno a nivel de repo — solo el filtro en memoria de `AddScheduleUseCase` | ver F16 |

La distinción entre la última columna importa: `owners.dni` y `sport.name_sport` implementan su chequeo de
"ya existe" llamando a un método `findByX` que Spring Data genera esperando **como mucho un resultado**
(`Optional<Entity>`) — si la tabla ya tiene dos filas con ese valor, la consulta no devuelve "sí, existe
más de uno", **lanza una excepción** (`IncorrectResultSizeDataAccessException`, JPA `NonUniqueResultException`
por debajo). `branches.name` y `court.name`, en cambio, usan un método `existsByX` real (`boolean`,
generado por Spring Data como `EXISTS`/`COUNT`), que es indiferente a cuántas filas matcheen — sigue
funcionando bien aunque haya duplicados. Ninguna auditoría previa había hecho esta distinción explícita
entre los dos estilos de implementación del mismo tipo de chequeo; es la base de la actualización de F3 y
F5 y del hallazgo nuevo F16.

## Performance

Sin cambios: F8 (listados admin sin paginar), F9 (historial sin orden), `findActiveByCourtAndDay` trayendo
de más (mismo mecanismo que F4), `findNearby` con Haversine sin bounding box — todos re-verificados sin
cambios de severidad.

## Production Readiness

Sin cambios: sin Actuator/health-check (F15), sin configuración explícita de logging (F15), perfil `dev`
correctamente aislado, manejo de errores consistente salvo F7. Un matiz nuevo relevante a F16: el handler
genérico de `GlobalExceptionHandler` (`@ExceptionHandler(Exception.class)`) captura y logea cualquier
excepción no anticipada con `log.error(..., ex)` (incluye stack trace) antes de devolver 500 — así que un
episodio de F16 quedaría en el log del lado del servidor, pero el cliente recibe únicamente "Ocurrió un
error inesperado" sin ninguna pista de que la causa es un horario duplicado corregible por un admin.

---

## Findings

### F16 — 🟠 High — `AddScheduleUseCase` sin lock ni constraint: una carrera puede duplicar horarios de una cancha/día y romper la disponibilidad/reserva de esa cancha con un 500 persistente

- **Confianza:** Confirmado
- **Evidencia:** `AddScheduleUseCase.java:51-61` — `scheduleRepository.findAllByCourtId(courtId)` (sin
  lock) trae los horarios existentes, `filterConflicts()` descarta en memoria los nuevos que solapan con
  los existentes, y recién después `scheduleRepository.saveAll(unique)` persiste. No hay ninguna
  transacción que aísle esta lectura de la escritura de otra ejecución concurrente del mismo método.
  `availability` (`V1__baseline.sql:109-118`) no tiene ningún `UNIQUE` sobre `(court_id, day_of_week)` —
  solo un índice no-único. La consulta que consume esa tabla asumiendo unicidad,
  `ScheduleRepository.findByCourtIdAndDay(long, DayOfWeek)` (`ScheduleRepository.java:26`), devuelve
  `Optional<ScheduleEntity>` — Spring Data genera esta consulta esperando 0 o 1 resultado. Esa misma
  consulta es la que usa `ScheduleGatewayAdapter.findByCourtAndDay()` (`ScheduleGatewayAdapter.java:18-21`),
  invocada por tres casos de uso: `GetAvailableSlotsUseCase.java:37`, `BookReservationUseCase.java:49` y
  `RescheduleReservationUseCase.java:77`. El endpoint que dispara la carrera,
  `POST /api/schedules/court/{idCourt}` (`ScheduleController.java:30-42`), es alcanzable por el propio
  Owner de la cancha o por un Admin — no hace falta coordinar dos usuarios distintos, un doble clic o un
  doble submit del mismo formulario alcanza.
- **Impacto:** Dos requests concurrentes de alta de horario para la misma cancha, con rangos que se
  solapan o son idénticos, pueden pasar ambos el chequeo `filterConflicts()` (cada uno ve el estado de la
  tabla *antes* de que el otro haya insertado) e insertar dos filas de `availability` para el mismo
  `(court_id, day_of_week)`. A partir de ese momento, **toda llamada a
  `GET /api/reservations/court/{courtId}/slots` (disponibilidad), a reservar, o a reprogramar una reserva
  para esa cancha en ese día específico** ejecuta `findByCourtIdAndDay`, encuentra 2 filas en vez de 0 o 1,
  y Spring lanza `IncorrectResultSizeDataAccessException` — no es un `BusinessException` ni un
  `DataIntegrityViolationException`, así que cae en el handler genérico de `GlobalExceptionHandler` y
  el cliente recibe **500 Internal Server Error** en vez de la lista de horarios o la confirmación de
  reserva. El problema no se autocorrige: sigue ocurriendo en cada intento hasta que alguien borre o
  fusione manualmente la fila duplicada directamente en la base.
- **Explicación:** Es el mismo defecto de fondo que F4 ya nombra (las mutaciones de Schedule no replican
  el lock pesimista que sí tienen `Book`/`Reschedule`/los `Delete`), pero aplicado al propio método de alta
  en vez de a su interacción con una reserva concurrente — y con una consecuencia distinta y más grave:
  F4 describe una inconsistencia de negocio silenciosa (una reserva que queda fuera del horario vigente,
  sin que nada lo note); acá el resultado es una falla dura y visible (500) en el flujo central del
  producto — ver disponibilidad y reservar — para esa cancha específica, disparable por el mismo actor que
  hizo el alta, sin necesidad de que intervenga un segundo usuario ni una ventana de negocio especial (un
  Owner reduciendo un horario). Es, en ese sentido, más fácil de alcanzar por accidente que el escenario
  de F4.
- **Recomendación:** Aplicar el mismo patrón que ya usa el proyecto en `Book`/`Reschedule`/`Delete*`: tomar
  un lock (pesimista sobre `Court`, o específicamente sobre las filas de `availability` de esa cancha) como
  primera operación de `AddScheduleUseCase.execute()`, antes de leer los horarios existentes. Complementar
  con un `UNIQUE KEY` en `availability` que impida dos filas para el mismo `(court_id, day_of_week)` — nota:
  si el modelo de negocio permite legítimamente múltiples franjas no solapadas el mismo día (mañana/tarde),
  una `UNIQUE` simple sobre `(court_id, day_of_week)` sería demasiado estricta; en ese caso el lock por sí
  solo (sin constraint) sigue cerrando la carrera, a costa de no tener el respaldo de base que sí tienen
  `Book`/`Reschedule`. Verificar cuál es el caso antes de elegir. Separadamente, cambiar
  `findByCourtIdAndDay` para tolerar más de una fila (devolver `List` en vez de `Optional`, o agregar
  `LIMIT 1`/`findFirstBy...OrderBy...`) evitaría que un duplicado ya existente en la base siga generando
  500 mientras no se corrija — una red de seguridad barata independiente del fix de la carrera en sí.

---

## Findings re-verificadas sin cambios (F1, F6–F15)

Re-leídas contra el archivo y línea citados en `docs/software-review-2026-08-30.md`, no contra el resumen
de esa auditoría — todas se sostienen exactamente como se describieron, sin cambio de severidad ni de
confianza. Se listan solo con su título y el resultado de la re-verificación puntual hecha en esta pasada;
el template completo de cada una sigue siendo el de la auditoría del 30/08 y no se repite acá para no
duplicar contenido idéntico.

- **F1 — 🟠 High (Riesgo potencial)** — Rate limiter de login sin manejo de proxy. Mecanismo re-confirmado
  sin cambios en el código. **Actualización de evidencia externa** (no de código): el loop
  `docs/loop/F1.md` confirmó con documentación oficial de Railway que sí existe un proxy (Edge Network)
  delante de la app — descarta la rama "tal vez no hay proxy" que la auditoría anterior dejaba abierta —
  pero encontró respuestas contradictorias del propio staff de Railway sobre qué header (`X-Forwarded-For`
  vs. `X-Real-IP`) y qué posición de la lista son confiables hoy. Esto no cambia la severidad (sigue siendo
  Alto/Riesgo potencial: el mecanismo del bug está confirmado, la corrección específica no puede elegirse
  con confianza todavía) pero sí afina la recomendación: antes de tocar código, resolver la ambigüedad de
  forma empírica contra el despliegue real (loggear los headers crudos de un request real) en vez de
  confiar en documentación de terceros que se demostró contradictoria — o considerar la alternativa,
  señalada en el loop, de limitar por email/cuenta en vez de por IP, que evita depender de la respuesta de
  Railway.
- **F2 — 🔵 Low** — `owner_club` sin PK/UNIQUE, promesa de V1 incumplida. Sin cambios; sigue sin ser
  explotable hoy (único caller siempre genera un `ownerId` fresco).
- **F6 — 🔵 Low** — 8 métodos muertos en `CourtRepository`/`ClubRepository`. Re-verificado por grep de
  referencias para 2 de los 8 (`findApprovedWithEagerLoading`, la query con lock de
  `findActiveByCourtAndDay`) — mismo resultado.
- **F7 — 🔵 Low** — `ErrorResponse.validationErrors` nunca poblado. Re-confirmado leyendo
  `GlobalExceptionHandler` completo en esta pasada.
- **F8 — 🔵 Low** — Listados admin sin paginar (`GetAllOwnersUseCase`, `GetAllCourtsUseCase`). Sin cambios.
- **F9 — 🔵 Low** — Historial de reservas del jugador sin orden explícito. Sin cambios.
- **F10 — 🔵 Low** — `spring-boot-starter-oauth2-client` sin uso. Re-confirmado, grep vacío en esta pasada.
- **F11 — 🔵 Low** — Cero tests directos para el código de autenticación. Sin cambios.
- **F12 — 🔵 Low** — Quinto nivel de test no documentado, `OwnerServiceTest` mal ubicado. Sin cambios.
- **F13 — 🔵 Optional** — `ReservationEntity.cancel()` muerto. Sin cambios.
- **F14 — 🔵 Low** — `desactivate` vs `deactivate`. Re-confirmado por grep de los tres controllers en esta
  pasada.
- **F15 — 🔵 Low** — Sin Actuator ni logging explícito. Sin cambios.

### F3 — 🟡 Medium → actualizada con nueva evidencia (severidad se mantiene en Medium, ver razonamiento)

`owners.dni` sin constraint de unicidad — mecanismo original (dos registros concurrentes con el mismo DNI
vía `RegisterOwnerUseCase` u `OwnerGatewayAdapter.register()`) sigue Confirmado sin cambios.

**Evidencia nueva de esta pasada:** el chequeo `OwnerRepositoryPort.existsByDni()` que ambos casos de uso
llaman para prevenir el duplicado está implementado en `OwnerRepositoryAdapter.java:67-68` como
`ownerRepository.findByDni(dni).isPresent()`, sobre `OwnerRepository.findByDni(long)`
(`OwnerRepository.java:12`), que devuelve `Optional<OwnerEntity>`. Confirmado por lectura de ambos
callers (`RegisterOwnerUseCase.java:30`, `OwnerGatewayAdapter.java:26`) que los dos pasan por esta misma
implementación.

**Impacto adicional:** una vez que la carrera original produce un par de filas con el mismo `dni` (aunque
sea una sola vez, en cualquier momento de la vida del sistema), **el propio chequeo que se supone que
previene nuevos duplicados para ese DNI se rompe**: la próxima vez que cualquiera intente registrar un
owner con ese mismo DNI (el mismo atacante, un tercero sin intención maliciosa reutilizando el número por
error, o incluso un reintento legítimo del mismo usuario que sufrió la carrera), `existsByDni` ya no
devuelve `true` de forma confiable — Spring Data encuentra 2 filas donde esperaba 0 o 1 y lanza
`IncorrectResultSizeDataAccessException`, que cae en el handler genérico y responde 500. El registro de
owners para ese DNI específico queda roto (ni permite duplicar más, ni deja pasar un intento legítimo con
información correcta) hasta que alguien limpie la fila duplicada directamente en la base.

**Por qué la severidad se mantiene en Medium y no sube a High:** el peor resultado sigue dependiendo de
alcanzar primero la carrera original (dos registros concurrentes con el mismo DNI, un evento poco frecuente
en un flujo de alta administrativa), y el radio de impacto del 500 resultante queda acotado a intentos de
registro futuros con ese DNI puntual — no afecta a otros owners ni al resto del sistema. Es una consecuencia
peor de lo que describía la ficha original (de "duplicado silencioso" a "duplicado + rotura del propio
chequeo"), pero no cambia la clase de evento que la dispara.

**Recomendación (sin cambios respecto al 30/08, reforzada):** agregar `UNIQUE KEY uq_owners_dni (dni)` en
una migración nueva sigue siendo la corrección correcta y ahora resuelve dos problemas a la vez — cierra la
carrera original y, al garantizar que nunca haya más de una fila, hace que `findByDni(...).isPresent()`
vuelva a ser seguro sin tener que tocar su implementación.

### F5 — 🔵 Low → actualizada con nueva evidencia, solo para `sport.name_sport` (severidad de esa parte sube a 🟡 Medium; `branches.name` y `court.name` no cambian)

**Evidencia nueva de esta pasada — distinción importante dentro de F5 que la auditoría anterior no hacía:**
de las tres columnas que F5 agrupa bajo "nombre único sin respaldo de base", solo `sport.name_sport` tiene
el mismo problema de implementación que `owners.dni`. `SportRepositoryAdapter.existsByNameIgnoreCase()`
(`SportRepositoryAdapter.java:44-45`) está implementado como
`sportRepository.findByNameSport(name.toUpperCase()).isPresent()`, sobre
`SportRepository.findByNameSport(String)` (`Optional<SportEntity>`) — mismo patrón que F3.
`branches.name` y `court.name`, en cambio, usan `existsByNameIgnoreCaseAndClub_Id` y
`existsByNameAndBranch_IdAndSport_Id` (`BranchRepositoryAdapter.java:72-73`,
`CourtRepositoryAdapter.java:60-61`) — métodos `existsBy...` reales, generados por Spring Data como
`EXISTS`/`COUNT`, indiferentes a cuántas filas matcheen. Esos dos **no** heredan el problema.

**Impacto adicional (solo para `sport`):** igual que F3 — una vez que dos deportes con el mismo nombre se
crean por la carrera original, el chequeo `existsByNameIgnoreCase` se rompe para ese nombre específico y
cualquier intento futuro de crear (o simplemente validar la existencia de) un deporte con ese nombre
devuelve 500 en vez de la respuesta correcta.

**Severidad:** se separa esta columna del resto de F5 y sube a 🟡 Medium por la misma razón que F3 (el
propio chequeo de duplicado queda roto, no solo el duplicado original) — el resto de F5 (`branches.name`,
`court.name`) se mantiene en 🔵 Low tal como estaba, porque ahí el chequeo no se rompe aunque exista un
duplicado.

**Recomendación:** sin cambios respecto al 30/08 para las tres — `UNIQUE (name_sport)` en `sport`,
`UNIQUE (club_id, name)` en `branches`, `UNIQUE (branch_id, sport_id, name)` en `court` — con la misma nota
de verificar duplicados existentes antes de aplicar. Para `sport` en particular, esto también repara el
propio método de chequeo, igual que en F3.

---

## Audit Validation

- **F16 — Confirmed.** Re-trazado desde `AddScheduleUseCase` hasta los tres consumidores de
  `findByCourtIdAndDay` (`GetAvailableSlotsUseCase`, `BookReservationUseCase`, `RescheduleReservationUseCase`)
  y hasta el endpoint HTTP real que dispara la carrera. Búsqueda deliberada de qué contradiría el hallazgo:
  se verificó que `availability` no tiene ningún `UNIQUE` (confirmaría que la carrera es posible a nivel de
  base) y que `findByCourtIdAndDay` es `Optional`, no `List` (confirma que 2 filas producen excepción en vez
  de degradar con gracia); ninguna de las dos verificaciones contradijo el hallazgo. Severidad Alta
  sostenida: trigger plausible (doble submit de un único actor, sin coordinación), consecuencia dura y
  visible (500 en el flujo central de reserva), sin auto-recuperación.
- **F3 — Upgraded (evidencia, no severidad):** el mecanismo original se mantiene Confirmado/Medium; se
  agrega la consecuencia de la rotura del propio chequeo `existsByDni` tras el primer duplicado, verificada
  releyendo la implementación real (`findByDni(...).isPresent()`), no asumida por analogía con F16.
- **F5 — Upgraded (severidad parcial):** re-abierta para verificar si las tres columnas que agrupa
  comparten el mismo mecanismo de implementación — no lo comparten. Se separó `sport.name_sport` (mismo
  patrón que F3, sube a Medium) de `branches.name`/`court.name` (usan `existsBy` real, se mantienen en Low
  sin cambios). Este es exactameente el tipo de ajuste que la metodología pide: no asumir que todos los
  miembros de una familia de hallazgos comparten el mismo mecanismo solo porque comparten el mismo síntoma
  superficial.
- **F1, F2, F4, F6–F15 — Confirmed**, sin cambios de severidad. Re-leídos contra el archivo/línea citados
  en la auditoría del 30/08 en esta misma pasada (no se asumió que seguían siendo ciertos solo porque el
  `git diff` da vacío) — spot-checks explícitos hechos: `oauth2` (F10), los tres controllers de
  desactivación (F14), `GlobalExceptionHandler` completo (F7), referencias a `findApprovedWithEagerLoading`
  y a la query con lock de `findActiveByCourtAndDay` (F6, F4).

## New Findings

**F16** (ficha completa arriba) es el único hallazgo genuinamente nuevo de esta pasada — ningún informe
anterior lo había reportado. Surgió de aplicar el mismo chequeo que produjo F3/F5 en la auditoría anterior
(toda columna con semántica de identidad, no solo la que ya se ve sospechosa) a una tabla que esa pasada no
había cubierto en el mismo detalle (`availability`), cruzado con el chequeo de "todo query method con un
caller real" del deep-dive de índices — la combinación de ambos (una columna sin respaldo de unicidad +
una consulta de resultado único que la consume) es lo que reveló la consecuencia de crash, no solo la
carrera en sí.

Las actualizaciones a **F3** y **F5** no son hallazgos nuevos con ID propio — son la misma pregunta
("¿qué pasa después de que la carrera ocurre una vez?") aplicada retroactivamente a hallazgos ya existentes,
razón por la cual se documentan como actualización de esos IDs y no como F17/F18.

## Final Assessment

**Vara usada:** la misma que las cuatro auditorías previas — proyecto de portfolio/aprendizaje en etapa
temprana, instancia única, sin tráfico de producción real.

Con esa vara, el veredicto general no cambia respecto al 30/08: cero hallazgos Críticos, arquitectura y
manejo de la concurrencia central del negocio (reservar una cancha) sólidos y probados. Lo que sí cambia es
la lista de qué priorizar primero, porque esta pasada encontró que el mismo patrón de "identidad sin
respaldo de base" tiene una cola más larga de la que se había documentado — no solo produce datos
duplicados, sino que rompe el propio mecanismo diseñado para prevenirlos, y en el caso de `availability`
rompe además el flujo de reserva en sí.

**Qué priorizar primero, en orden (reemplaza el orden del informe anterior):**
1. **F16** — es nuevo, Alto, y el más fácil de alcanzar por accidente de toda la lista (un doble submit
   de un único Owner). Aplicar el mismo lock que ya usan `Book`/`Reschedule`/`Delete*` a
   `AddScheduleUseCase` antes que ningún otro ítem de esta lista.
2. Confirmar la topología de Railway de forma empírica y resolver F1 — sigue siendo la pieza que más
   depende de un hecho externo, y el loop de desarrollo ya demostró que la documentación de terceros no
   alcanza para resolverla con confianza.
3. `UNIQUE KEY uq_owners_dni` (F3) — ahora con una razón adicional para no posponerlo: hoy, si el gap se
   dispara una sola vez, deja el registro de owners roto para ese DNI hasta una intervención manual en la
   base, no solo un duplicado silencioso.
4. `UNIQUE (name_sport)` en `sport` (parte de F5 que ahora es Medium) — mismo razonamiento que el punto 3,
   a menor escala.
5. Redirigir `ReservationRepositoryAdapter.findActiveByCourtAndDay()` a la query con lock que ya existe
   (F4) — sigue siendo un cambio de una línea con beneficio de concurrencia y de performance.
6. Tests directos para `LoginAttemptService`/`JwtUtil`/`JwtFilter` (F11).
7. El resto (F2, F6, F7, F8, F9, F10, F12, F13, F14, F15, y la parte de F5 que sigue en Low) — mejoras
   genuinas de bajo riesgo si se posponen.

**Lo que esta revisión no pudo verificar** (sin cambios respecto al 30/08, se mantienen abiertas):
- Si la suite de tests pasaría igual ejecutándola ahora mismo — no se ejecutó en esta sesión.
- Volumen real de datos en cualquier tabla.
- Topología de red real de Railway más allá de lo que confirmó el loop de F1 (existe un proxy; sigue sin
  resolverse qué header/posición confiar con una fuente no contradictoria).
- Si existen hoy filas duplicadas en `owner_club`, `owners.dni` o `sport.name_sport` en una base ya
  desplegada.

**Sobre el valor de esta pasada dado que el código no cambió:** el Skill advierte explícitamente contra
tratar "todo Confirmado, sin cambios" como señal de que la validación fue superficial — y en efecto, la
mayoría de los quince hallazgos anteriores se sostuvieron sin cambios porque el código es idéntico, no
porque esta pasada los haya tratado con menos rigor. Donde sí se insistió más allá de la simple
re-confirmación (la distinción `findBy(...).isPresent()` vs. `existsBy...` real, aplicada sistemáticamente
a las columnas de identidad ya conocidas) apareció tanto una actualización real a dos hallazgos existentes
como un hallazgo nuevo de severidad Alta — evidencia de que valió la pena correr el Skill de nuevo incluso
sin cambios de código, en vez de asumir que un informe reciente lo dejaba todo cerrado.
