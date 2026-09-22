# Software Review

**Método:** `.claude/skills/software-review/SKILL.md` — auditoría de dos fases, de solo lectura.
**Rama/commit auditado:** `docs/audit-2026-08-25` @ `a5d0cfe` (idéntico a `development` @ `9a5e365` más un commit que solo agrega un doc). `git diff a5d0cfe -- src pom.xml` está vacío: el código no cambió desde las auditorías previas.
**Independencia de esta pasada:** el repo ya tenía, sin commitear, `docs/audit-2026-08-25.md`, `docs/audit-database-2026-08-26.md` y una versión previa de este mismo informe (`docs/software-review-2026-08-26.md`), los tres sobre el mismo código exacto. Por pedido explícito de la autora, esta pasada se hizo **desde cero, sin abrir `software-review-2026-08-26.md` durante el análisis** — cada hallazgo de este informe fue re-derivado leyendo el código, el schema y las migraciones directamente. La comparación contra los tres informes anteriores se hizo recién al final (ver notas de comparación al pie de cada hallazgo relevante y el Final Assessment). Una excepción honesta: antes de que la autora confirmara "auditoría fresca", alcancé a ver de pasada el resumen ejecutivo, la lista numerada de hallazgos y la conclusión de `software-review-2026-08-26.md` (para decidir qué pedirle a la autora). Donde eso pudo influir el resultado, lo marco explícitamente.
**Contexto del proyecto (vara usada en todo el informe):** proyecto individual de portfolio/aprendizaje, Java 21 + Spring Boot 3.5.5, arquitectura hexagonal + casos de uso, desplegado en Railway como instancia única, sin tráfico de producción real.

---

## Executive Summary

| Severidad | Confirmado | Riesgo potencial | Mejora opcional |
|---|---|---|---|
| 🔴 Critical | 0 | 0 | 0 |
| 🟠 High | 0 | 1 | 0 |
| 🟡 Medium | 3 | 0 | 0 |
| 🔵 Low | 12 | 0 | 4 |

El proyecto sostiene, contra el código real, casi todo lo que documenta: dominio inmutable con invariantes encapsuladas en los agregados, orden correcto del lock pesimista en `Book`/`Reschedule`/`DeleteCourt`/`DeleteBranch` (verificado leyendo cada caso de uso línea por línea), separación limpia dominio/puerto/adapter, autorización por ownership contra la base (no contra el token) en los tres beans `@courtAuthorization`/`@branchAuthorization`/`@clubAuthorization`, patrón IDOR 404-en-vez-de-403 en reservas, jerarquía `BusinessException` sin excepciones huérfanas, validación explícita de `CORS_ALLOWED_ORIGINS` al arrancar, y las 12 DTOs de request con anotaciones de validación (`@NotNull`/`@Valid`/`@Positive`/etc.).

El hallazgo de mayor severidad (F1) es el mismo que documentan las tres auditorías previas — no es una regresión de código, sino una pregunta externa sin resolver: la fuente de IP que usa el rate limiter de login. El resto del informe encuentra un patrón más interesante que las auditorías previas no habían nombrado como tal: **el sistema tiene exactamente una garantía de concurrencia tratada con todo el rigor (lock pesimista + constraint única de base) — la de "no doble reserva" — y varias invariantes de identidad/unicidad estructuralmente idénticas que se validan solo en memoria de aplicación, sin ningún respaldo a nivel de base** (F2, F3). Investigar cada una a fondo (quién más llama a ese código, qué IDs puede producir cada camino real) importó: una de las tres que parecían el mismo bug al leer el código por primera vez resultó no ser explotable hoy con ningún camino de código existente (ver F2 y su Confidence).

---

## Architecture

Hexagonal / Ports & Adapters + Use Cases, tal como documenta el README, verificado por lectura directa (no por confianza en el documento):

- `domain/model/` son `record`s inmutables (`Reservation`, `Court`, `Branch`, `Club`, `TimeSlot`, `Ticket`) con las transiciones de estado y sus invariantes encapsuladas ahí — confirmado en `Reservation.cancel()`/`markAsRescheduled()`, `Branch.approve()`/`activate()`, `Club.approve()`/`addOwner()`/`update()`. Ninguno depende de Spring ni de JPA.
- `domain/port/out/` (puertos "pesados", devuelven el agregado) y `application/port/out/` (gateways de solo lectura, snapshots) están genuinamente separados por uso real: `BookReservationUseCase` usa `CourtGateway.findByIdForUpdate` (snapshot con lock) mientras que `ReservationRepositoryPort` expone el agregado completo — no es una capa decorativa, cada una tiene un consumidor real.
- Los casos de uso son de una sola responsabilidad y no contienen lógica de negocio propia más allá de orquestación — la validan sus propios dominios (`Reservation.cancel()`, `Club.addOwner()`).
- Los adapters de `infrastructure/adapter/` hacen mapeo manual dominio↔entidad; los controllers son HTTP puro (mapeo DTO↔dominio vía `mapper/dto/`, `@PreAuthorize`, delegación).

No hay hallazgos nuevos de arquitectura en este informe: la que ya documentaron las auditorías previas (dominio inmutable, capas separadas) se sostiene. Un matiz que si vale la pena nombrar: la arquitectura hexagonal completa (dos familias de puertos, gateways separados, mappers dedicados) es más maquinaria de la que un CRUD de este tamaño necesitaría por sí solo — pero dado que el proyecto es explícitamente de portfolio/aprendizaje (demostrar el patrón es parte del objetivo, no un costo accidental) y que no se ve la "elaboración especulativa" (interfaces con un solo implementador trivial y nada más, capas que no separan nada real), no lo reporto como hallazgo. Es una decisión de contexto razonable, no sobre-ingeniería sin propósito.

## SOLID

Sin violaciones con consecuencia real más allá de lo que Clean Code / DRY ya cubren abajo (F6, F16). Un caso puntual: `ClubRepositoryAdapter.updateExistingEntity()` (líneas ~100-120) mezcla reconciliación de owners con el resto de la actualización de campos en un solo método — no es un problema de diseño grave (es corto, legible), pero es el método que hace la reconciliación check-then-act discutida en F2; si se decide agregar un lock o mover la reconciliación a su propio método más adelante, la separación ayudaría.

## Clean Code

- `GlobalExceptionHandler.handleValidationException` construye un `Map<String,String> validationErrors` y lo descarta: solo concatena su `.toString()` en el mensaje, sin nunca asignarlo al campo `ErrorResponse.validationErrors` que existe exactamente para esto (F7).
- Comentario desactualizado en `ClubEntity.java:24` ("Cubre todas las queries de listado: findApprovedWithEagerLoading, findApprovedPaginated") que ya no es cierto — ninguna de esas dos queries tiene un caller real (parte de F6).
- El resto del código de dominio y casos de uso tiene comentarios que explican decisiones ("por qué", no "qué") de forma consistente — un patrón notablemente mejor que el promedio, con la excepción puntual de `JwtUtil` (F-menor, ver Testing/Producción) donde los comentarios sí explican el qué línea por línea.

## DRY & Code Smells

- **Código muerto en repositorios** (F6): 6 métodos de consulta declarados en `CourtRepository` sin ningún caller real, y 2 en `ClubRepository` — uno de estos últimos es exactamente el antipatrón "`JOIN FETCH` de colección a-muchos + `Pageable`" que en otro lugar del mismo código (`ClubRepository.findByVerificationStatusAndActiveStatus`) los propios autores ya identificaron y evitaron a propósito (comentario: *"Paginado limpio sin JOIN FETCH de colecciones — evita el warning HHH90003004"*). Que el patrón problemático solo sobreviva en código sin caller es la razón por la que esto es Low y no Medium/High — no está causando el problema de performance que causaría si estuviera vivo.
- Mapeo manual dominio↔entidad duplicado en 4 adapters (`OwnerRepositoryAdapter`, `PlayerRepositoryAdapter`, `SportRepositoryAdapter`, `ScheduleRepositoryAdapter`) — decisión ya documentada explícitamente en el README ("el costo de la abstracción no se justifica para 4 clases chicas"); re-verificado, sigue siendo cierto y sigue siendo una decisión razonable, no un hallazgo nuevo.
- `OwnerEntity.clubs` es `List<ClubEntity>` mientras que `ClubEntity.owners` (lado `mappedBy`) es `Set<OwnerEntity>` — dos tipos de colección distintos para los dos lados de la misma relación `@ManyToMany` (`OwnerEntity.java:33`, `ClubEntity.java:47`). No es la causa de F2 (la causa es la ausencia de constraint en la base, no el tipo de colección elegido en Java), pero es una inconsistencia que vale la pena unificar si se toca esta relación — mención como mejora opcional, no como hallazgo con impacto propio.

## Testing

**Cuatro niveles documentados, y un quinto sin documentar, re-confirmado:** además de dominio puro (`domain/`), casos de uso (Mockito), controllers (`@WebMvcTest`) y concurrencia (Testcontainers + MySQL real), existe un paquete `service/` (`ClubServiceTest`, `PlayerServiceTest`, `CourtServiceTest`, `ReservationServiceTest`, `CascadeDeletionRegressionTest`) con `@SpringBootTest` de integración completa contra H2, no mencionado en el README (F12). `OwnerServiceTest.java` está suelto en la raíz del paquete de test, y pese al nombre no prueba ningún `OwnerService` — ejercita `DateUtils.isOfLegalAge()` (`OwnerServiceTest.java:14-30`), que es un test de utilidad/dominio mal ubicado.

**Cobertura desigual, medida contra el reporte JaCoCo existente en `target/site/jacoco/`** (de una build anterior sobre este mismo código — no ejecuté la suite en esta sesión, pero el código no cambió desde esa build, y `target/surefire-reports/*.txt` confirma **239 tests, 0 failures, 0 errors** consistente con lo que documenta el README):

| Paquete | Instrucciones cubiertas | Ramas cubiertas |
|---|---|---|
| `application.usecase.reservation` | 99% | 100% |
| `security.config` (JwtUtil, SecurityConfig, PasswordConfig, WebConfig) | 65% | 33% |
| `domain.model` | 55% | 43% |
| `dto.request` | 42% | n/a |
| `dto.response` | 37% | n/a |
| `mapper.dto` | 17% | 3% |
| **`security.service` (JwtUtil consumers: JwtFilter, LoginAttemptService, AuthService, UserDetailsServiceImpl)** | **16%** | **0%** |

`application.usecase.reservation` al 99%/100% es sobresaliente y consistente con el commit reciente `ed029a1` ("add unit coverage for Cancel/Reschedule/GetAvailableSlots/GetPlayerReservations"). En el otro extremo, **`security.service` en 16% de instrucciones y 0% de ramas no es casualidad: `find src/test -iname "*Jwt*" -o -iname "*LoginAttempt*" -o -iname "*AuthService*" -o -iname "*UserDetailsService*"` no devuelve ningún archivo — cero tests unitarios directos para `JwtFilter`, `LoginAttemptService`, `AuthService` o `UserDetailsServiceImpl`** (F11). Esto es relevante más allá del número: es exactamente el código que rodea al único hallazgo Alto de este informe (F1) — la lógica de `LoginAttemptService.isBlocked`/`registerFailure` (los umbrales de 5 intentos y 15 minutos) no tiene ni un test que la ejercite directamente hoy, solo indirectamente a través de lo que toquen los tests de controller/integración.

`mapper.dto` al 17% es más benigno de lo que parece: son mappers generados/manuales de mapeo campo a campo sin lógica condicional real (confirmado leyendo `AddressMapper`, `ClubMapper`) — el riesgo de un bug no descubierto ahí es bajo, pero el número en sí mismo sigue siendo una pregunta abierta que ninguna auditoría (incluida esta) puede cerrar sin ejecutar la suite con más instrumentación.

Sin test de la carrera de `owner_club` — correcto no tenerlo, porque (ver F2) esa carrera no es alcanzable con el código actual, así que un test de esa carrera específica probaría algo que no puede pasar. Si en el futuro se agrega un endpoint que vincule un owner **existente** a un club por ID, ese es el momento de agregar tanto el lock/constraint como el test.

## Security

- **CORS**: `SecurityConfig.validateCorsAllowedOrigins()` (líneas 44-63) falla el arranque si `CORS_ALLOWED_ORIGINS` queda vacía — confirmado, hace exactamente lo que el README dice.
- **Autorización por ownership contra la base**: los tres beans (`CourtAuthorization`, `BranchAuthorization`, `ClubAuthorization`) hacen una query real (`existsByCourtAndOwner`, `existsByIdAndClub_Owners_Id`, `existsByIdAndOwners_Id`) contra el id efectivo del `Authentication` — no confían en nada que venga del cliente más que el JWT ya validado. Confirmado en las tres clases.
- **IDOR en reservas**: `CancelReservationUseCase.java:38-41` y el mecanismo equivalente en `RescheduleReservationUseCase` devuelven `ReservationNotFoundException` (404) en vez de un 403 cuando `!reservation.belongsTo(playerId)` — confirmado, con el comentario explícito en el código citado por las auditorías previas.
- **Validación de entrada**: las 12 DTOs de `dto/request/` tienen anotaciones de validación (`@NotNull`, `@Valid`, `@Positive`, `@FutureOrPresent`, etc.) — revisado explícitamente, sin excepciones.
- **F1 — Rate limiter de login sin manejo de proxy** (re-confirmado independientemente, ver Findings): `AuthController.java:25` pasa `httpRequest.getRemoteAddr()` directo a `AuthService.login()`. Sin `server.forward-headers-strategy` en ninguno de los dos `application*.properties`, sin `ForwardedHeaderFilter`, sin `Dockerfile` ni configuración de Railway en el repo que confirme o descarte un proxy delante de la app.
- **Dependencia sin uso**: `spring-boot-starter-oauth2-client` en `pom.xml` — `grep -rn "oauth2\|OAuth2" src/main/java` no devuelve ningún resultado (F10).
- **Rate limiting en memoria**: ya documentado como limitación conocida en el README y en la auditoría previa (asume instancia única de Railway); re-verificado, sigue siendo así (`LoginAttemptService.java:19-21`), no es un hallazgo nuevo.
- No se encontraron secretos en `src/main/resources` (usa `${...}` en todos los casos); el JWT secret literal en `src/test/resources/application-test.properties` ya lo documentó la auditoría del 25/08 como de impacto nulo (solo perfil test, H2 en memoria) — re-verificado, sigue siendo cierto.

## Concurrency

**El patrón de referencia, correcto, ya está en el código** — y sirve de vara para todo lo demás: `BookReservationUseCase.execute()` toma `courtGateway.findByIdForUpdate(courtId)` (lock pesimista sobre `Court`) como **primera** operación de la transacción, antes de cualquier lectura de disponibilidad. `RescheduleReservationUseCase` replica el mismo orden derivando el `courtId` primero (`findCourtIdByReservationForUpdate`). `DeleteCourtUseCase` y `DeleteBranchUseCase` hacen lo mismo antes de chequear `hasReservations()`. Los cuatro están probados contra MySQL real con Testcontainers (`BookReservationConcurrencyTest`, `RescheduleReservationConcurrencyTest`, `BookReservationVsDeleteCourtConcurrencyTest`, `BookReservationVsDeleteBranchConcurrencyTest`) — confirmado leyendo cada test, no solo su nombre.

**El mismo patrón no se replicó en las mutaciones de Schedule** (F4): `AddScheduleUseCase`, `UpdateScheduleUseCase` y `DeleteScheduleUseCase` no toman ningún lock sobre `Court` ni sobre las filas de `reservation`/`availability` involucradas, pese a que `UpdateScheduleUseCase` y `DeleteScheduleUseCase` sí hacen un check-then-act sobre el mismo recurso que `BookReservationUseCase` puede modificar concurrentemente (reservas activas de esa cancha/día). Evidencia de que esto no fue una omisión completamente accidental: `ReservationRepository.java` define `findActiveByCourtAndDay` (líneas 47-55) con `@Lock(PESSIMISTIC_WRITE)`, y el índice `idx_reservation_court_day_status` en `ReservationEntity.java:19-20` está comentado explícitamente como *"Cubre findActiveByCourtAndDay (verificación de disponibilidad + pessimistic lock)"* — pero **esa query con lock no tiene ningún caller**: `ReservationRepositoryAdapter.findActiveByCourtAndDay()` (línea 70) en realidad llama a `reservationRepository.findActiveByCourt()` (sin lock, sin filtro de día — filtra el día después, en Java, línea 74). El lock que el propio índice dice que existe para esto, no se ejecuta nunca.

Investigación de alcance real (para no reportar una carrera de juguete): dos transacciones que no tocan ninguna fila en común no se serializan entre sí solo porque una de ellas mencione "lock" en un comentario. `UpdateScheduleUseCase` solo toca la tabla `availability`; `BookReservationUseCase` toma su lock sobre `court`. Ninguna de las dos operaciones bloquea a la otra a nivel de motor. Secuencia concreta y alcanzable: T1 (`UpdateScheduleUseCase`, Owner) lee las reservas activas del día X para la cancha C (ninguna reserva aún fuera del rango nuevo 10-16), decide que el nuevo horario 10-16 es válido; en paralelo, T2 (`BookReservationUseCase`, Player) reserva 17:00 el mismo día X para la misma cancha C, válido bajo el horario *viejo* (10-18) que todavía rige cuando T2 lee la configuración; T1 commitea el nuevo horario 10-16; T2 commitea la reserva de las 17:00. Resultado: una reserva `RESERVADO` a las 17:00 en una cancha cuyo horario vigente ya no la permite — no es doble reserva ni pérdida de datos, es una reserva que queda fuera del horario comercial vigente sin que nada lo detecte. La misma falta de lock aplica a `DeleteScheduleUseCase` (chequea `existsReservationForDay` sin lock antes de borrar la franja) — un `Book` concurrente entre el chequeo y el borrado deja una reserva activa sin ninguna franja de horario que la respalde.

Esto requiere una ventana de timing específica y una condición de negocio concreta (un Owner reduciendo su horario justo cuando un Player reserva en el margen que va a desaparecer) — no es tan alcanzable como el escenario de doble-reserva que el código sí protege, pero el mecanismo es real, el código para arreglarlo ya existe a medias (la query con lock, sin usar) y ningún test lo cubre.

**Otros check-then-act sin lock, investigados uno por uno para determinar si son alcanzables hoy:**

- `AddOwnerToClubUseCase` (ver F2 en Findings) — el check-then-act existe (`Club.addOwner()` valida contra el set cargado en memoria antes de `save()`), pero **no es alcanzable**: el único camino que llama a este código (`OwnerGatewayAdapter.register()`) siempre inserta un Owner nuevo antes de vincularlo — nunca hay dos llamadas concurrentes compitiendo por el mismo `(ownerId, clubId)`, porque cada llamada genera su propio `ownerId` nuevo. Mismo principio que ya usa este proyecto para el fix real de `RescheduleReservationUseCase` (ver metodología del Skill) aplicado en sentido inverso: acá confirma que el missing-lock es solo defensa en profundidad, no un bug activo.
- `RegisterOwnerUseCase` / `OwnerGatewayAdapter.register()` (ver F3) — **sí alcanzable**: son dos entry points HTTP distintos (`POST /api/owners` admin, y `POST /clubs/owner/{idClub}/owners`) que ejecutan la misma secuencia `existsByDni → existsByCuil → existsByEmail → save()` sin lock, insertando cada uno una fila con un `dni` provisto por quien llama. Nada impide que dos llamadas concurrentes con el mismo DNI pasen ambas el chequeo.
- `CreateBranchUseCase` / `CreateCourtUseCase` / `CreateSportUseCase` (ver F5) — alcanzables de la misma forma (dos submits concurrentes con el mismo nombre), impacto menor (duplicado de contenido, no de identidad legal).

## Database & Indexes

**Fuente de verdad del esquema**: Flyway, exclusivamente en todo ambiente fuera de `dev` (`spring.jpa.hibernate.ddl-auto=validate` en `application.properties`); el perfil `dev` usa `ddl-auto=update` de forma explícita y documentada, opt-in. `V1__baseline.sql` fue tomado de un volcado real (`real_schema.sql`, `SHOW CREATE TABLE` codificado en UTF-16 — decodificado y comparado línea por línea contra la migración: coinciden exactamente para `owner_club`, sin PK ni UNIQUE en ninguno de los dos).

**Migraciones leídas contra lo que ejecutan, no contra lo que dicen que van a hacer** (metodología obligatoria del Skill) — encontré una promesa incumplida real: `V1__baseline.sql:70` dice *"V2 agrega PRIMARY KEY (owner_id, club_id) previa limpieza de duplicados"*. `V2__add_unique_reservation_slot.sql` no toca `owner_club` en absoluto — agrega la columna generada y el índice único de `reservation` (que sí cumple lo que promete). `V3__add_branch_cancellation_window.sql` tampoco. No hay una `V4`. La promesa de V1 sobre `owner_club` nunca se cumplió (parte de F2).

**Índices y queries, correlacionados uno por uno** para `reservation`, `court`, `branches`, `clubs`: cubiertos correctamente para todos los caminos de lectura vivos que rastreé —
- `idx_reservation_court_day_status (court_id, reservation_day, status)` cubre `findStartTimesByCourtAndDay` (usado por `findBookedSlots`, la verificación de disponibilidad real).
- `idx_court_branch_active (branch_id, active_status)` cubre `findByBranch_IdAndActiveStatus` (paginado de canchas activas, con y sin `Pageable`).
- `uq_reservation_active_slot (active_slot_court_id, reservation_day, start_time)` es el respaldo de base para la garantía de no-doble-reserva — confirmado consistente con el lock de aplicación, no un sustituto de él.

**Código muerto en repositorios, confirmado método por método** (conteo de referencias en todo `src`, no solo intuición al leer):

| Método (repo) | Referencias totales (declaración incluida) | Estado |
|---|---|---|
| `CourtRepository.findByNameAndBranchIdAndSportId` | 1 | Muerto |
| `CourtRepository.findByIdAndBranch_Id` | 1 | Muerto |
| `CourtRepository.findByBranch_VerificationStatusAndBranch_ActiveStatus` (List, línea 23) | 2* | Muerto |
| `CourtRepository.findByBranch_VerificationStatusAndBranch_ActiveStatus` (Page, línea 51 — `LEFT JOIN FETCH c.schedules` + `Pageable`, el antipatrón) | 2* | Muerto |
| `CourtRepository.findByCourtWithSchedule` | 1 | Muerto |
| `CourtRepository.findBySport_Id` | 1 | Muerto |
| `CourtRepository.findByBranch_Id` (Page, línea 68 — mismo antipatrón) | 1 | Muerto |
| `ClubRepository.findApprovedWithEagerLoading` | 1 | Muerto |
| `ClubRepository.findApprovedPaginated` | 2* | Muerto |

\* Las dos referencias son las dos sobrecargas/su propia declaración, ninguna es un caller real — verificado abriendo cada línea.

Los métodos vivos que sí reemplazan a estos ya evitan el antipatrón a propósito: `findApprovedClean` (usado por `CourtRepositoryAdapter.findApprovedPaginated`, línea 82) solo hace `LEFT JOIN FETCH c.sport` (relación *-a-uno, segura con `Pageable`); `ClubRepository.findByVerificationStatusAndActiveStatus` (usado por `ClubRepositoryAdapter.findByStatus`) no hace `JOIN FETCH` de ninguna colección. El antipatrón existe en el código pero no se ejecuta nunca — es deuda para limpiar, no un problema de performance activo (F6).

**No verificable desde el repo**: volumen real de filas en cualquier tabla; si existen hoy filas duplicadas en `owner_club` en una base ya desplegada (la migración incumplida de V1 sugiere que en algún momento hubo — o se anticipaba que pudiera haber — duplicados que limpiar, pero no hay forma de confirmarlo sin acceso a esa base).

## Performance

- **Listados admin sin paginar**: `GetAllOwnersUseCase.execute()` (`GetAllOwnersUseCase.java:18`, vía `OwnerRepositoryPort.findAll()`) y `GetAllCourtsUseCase.execute()` (`CourtRepositoryPort.findAll()`) devuelven `List<T>` completas sin `Pageable`. Ambos son Admin-only (`OwnerController.getAll()` con `@PreAuthorize("hasRole('ADMIN')")`; `CourtAdminController`, verificado por separado). Con el volumen actual de un portfolio esto no tiene impacto medible; queda como Low porque la app ya tiene el mecanismo de paginación resuelto (`PageRequest`/`PageResult`) en el resto de los listados — extenderlo acá es consistencia, no una feature nueva (F8).
- **`findActiveByCourtAndDay` trae más filas de las necesarias** (mismo hallazgo que F4 desde el ángulo de performance, no solo de lock): al no filtrar por día en la query real ejecutada (`findActiveByCourt` sin el `AND r.day = :day` que sí tiene la query muerta), trae todas las reservas activas de la cancha en cualquier día y filtra en memoria (`ReservationRepositoryAdapter.java:74`). Acotado por "reservas activas de una cancha" — probablemente pequeño en este contexto, pero crece con el historial activo si el volumen aumenta. No verificable el impacto real sin datos de producción.
- **`GetPlayerReservationsUseCase` / historial sin orden explícito**: `findByPlayer_Id` es una derived query sin `OrderBy`; el orden de retorno no está garantizado (F9).
- **`findNearby` (búsqueda geográfica en `BranchRepository`)** usa la fórmula de Haversine completa por fila, sin bounding-box previo ni índice espacial — ya señalado en auditorías previas como optimización condicionada al volumen de sucursales, no verificable desde el repo; re-confirmado, sigue siendo así, no es un hallazgo nuevo.
- **N+1 documentado y deliberado** en el paginado de clubes (comentario explícito en `ClubRepositoryAdapter`/`ClubRepository` sobre resolver `owners` lazy dentro de la transacción) — decisión ya tomada y documentada, no un hallazgo.
- `GetAllSportsUseCase` (sin paginar) explícitamente revisado y **no** reportado como hallazgo: `sport` es una tabla de referencia, de escritura rara y Admin-only — exactamente el caso que el Skill pide no tratar igual que un listado de alto volumen.

## Production Readiness

- Sin `spring-boot-starter-actuator` en `pom.xml` — no hay health check ni endpoint de métricas. Para una instancia única en Railway sin SLA, el costo de no tenerlo es bajo hoy, pero es la pieza más barata de agregar de toda la lista (F15).
- Sin configuración explícita de logging (ni `logback-spring.xml` ni niveles por paquete en `application*.properties`) — confirmado por ausencia; se depende enteramente de los defaults de Spring Boot (F15).
- Perfil `dev` correctamente aislado y documentado — `ddl-auto=update` solo bajo activación explícita, nunca default; re-verificado, sostiene lo que dice el README.
- Sin `Dockerfile` en el repo, con `spring.docker.compose.enabled=false` — consistente con lo que ya documenta el README; no verificable desde el repo cómo Railway construye/arranca la app en su ausencia.
- Manejo de errores: `GlobalExceptionHandler` traduce consistentemente `BusinessException`, `DataIntegrityViolationException` (409, con el razonamiento correcto de que una violación de integridad en este dominio siempre es un conflicto de negocio conocido, nunca un 500), `MethodArgumentNotValidException`, `AccessDeniedException`, `BadCredentialsException`, `TooManyRequestsException`, y una red de seguridad genérica para lo no anticipado. El único defecto puntual es F7 (`validationErrors` nunca poblado).

---

## Findings

### F1 — 🟠 High — Rate limiter de login usa `getRemoteAddr()` sin manejo de proxy

- **Confianza:** Potencial riesgo
- **Evidencia:** `AuthController.java:25` — `authService.login(request, httpRequest.getRemoteAddr())`. `LoginAttemptService.java` indexa los intentos fallidos por esa IP en un `ConcurrentHashMap`. Sin `server.forward-headers-strategy` en `application.properties` ni `application-dev.properties`; sin `ForwardedHeaderFilter`; sin `Dockerfile` ni config de Railway en el repo.
- **Impacto:** Si Railway coloca la app detrás de un proxy/load balancer (patrón típico en PaaS), todos los requests de login llegarían con la misma IP interna. 5 intentos fallidos de cualquier usuario legítimo bloquearían el login de **todos los usuarios** por 15 minutos — una protección anti-fuerza-bruta convertida en vector de DoS trivial. Si Railway expone la IP real de forma transparente, el código funciona como está.
- **Explicación:** Spring Boot con Tomcat embebido no confía en headers de proxy por defecto.
- **Recomendación:** Confirmar la topología real de Railway (documentación o inspección de un request real). Si llega por `X-Forwarded-For`, configurar `server.forward-headers-strategy=framework` (o `native`).
- **Nota de comparación:** idéntico en mecanismo y severidad al hallazgo ya reportado en `docs/audit-2026-08-25.md` y en `docs/software-review-2026-08-26.md`. Re-derivado de forma independiente en esta pasada (no copiado); coincide.

### F3 — 🟡 Medium — `owners.dni` sin constraint de unicidad a nivel de base, a diferencia de su columna hermana `cuil`

- **Confianza:** Confirmado
- **Evidencia:** `V1__baseline.sql:23-27` — `dni BIGINT NOT NULL` sin `UNIQUE`, mientras la misma tabla tiene `UNIQUE KEY uq_owners_cuil (cuil)` dos líneas más abajo. `RegisterOwnerUseCase.java:30-33` y `OwnerGatewayAdapter.java:26-29` (dos entry points HTTP distintos: `POST /api/owners` y `POST /clubs/owner/{idClub}/owners`) validan `existsByDni` y `existsByCuil` con exactamente el mismo patrón check-then-act, sin lock, sin transacción que aísle la verificación de la escritura.
- **Impacto:** Dos registros concurrentes de Owner con el mismo DNI (vía cualquiera de los dos endpoints, o uno de cada uno) pasan ambos el chequeo `existsByDni` antes de que cualquiera confirme, resultando en dos filas de `owners` con el mismo DNI — un documento de identidad legal duplicado en el sistema, sin que nada a nivel de base lo impida.
- **Explicación:** El DNI y el CUIL tienen la misma semántica de identidad real (identificadores únicos de una persona), y el código de aplicación los trata igual — pero solo uno de los dos tiene respaldo de base. Es el ejemplo más limpio en este repo de una invariante enforced solo en memoria de aplicación, con un hermano en la misma tabla que sí tiene el respaldo correcto.
- **Recomendación:** Agregar `UNIQUE KEY uq_owners_dni (dni)` en una migración nueva. Antes de aplicarla contra una base ya poblada, verificar que no existan ya filas duplicadas (mismo cuidado que tomó `V1`/`V2` para `reservation`).

### F2 — 🔵 Low — `owner_club` sin `PRIMARY KEY` ni `UNIQUE`; promesa de migración incumplida (defensa en profundidad, no carrera activa hoy)

- **Confianza:** Confirmado (el gap de esquema y la promesa incumplida) / la carrera concreta que sugiere el nombre de este hallazgo **no es alcanzable hoy** — ver Explicación.
- **Evidencia:** `V1__baseline.sql:64-71` crea `owner_club` sin `PRIMARY KEY`, con el comentario *"V2 agrega PRIMARY KEY (owner_id, club_id) previa limpieza de duplicados"*. `V2__add_unique_reservation_slot.sql` y `V3__add_branch_cancellation_window.sql` no tocan esta tabla. Confirmado también contra el volcado real (`real_schema.sql`, decodificado de UTF-16, líneas 87-94): la tabla real tampoco tiene PK. `OwnerEntity.java:29-34` mapea el lado propietario del `@ManyToMany` sin ningún unique constraint declarado tampoco a nivel JPA.
- **Impacto:** Ninguno confirmado hoy. El único camino de escritura de esta tabla, `AddOwnerToClubUseCase.execute()` → `OwnerGatewayAdapter.register()` (`OwnerGatewayAdapter.java:24-42`), **siempre inserta un Owner nuevo primero** (`ownerRepository.save(owner)` con `owner.id() == null`) antes de vincularlo al club — por lo tanto ninguna ejecución concurrente puede competir por el mismo par `(ownerId, clubId)`, porque cada llamada genera su propio `ownerId` nuevo e irrepetible. `UpdateClubUseCase` no modifica `ownerIds`. No existe hoy ningún endpoint que vincule un owner **ya existente** a un club por ID.
- **Explicación:** Este es exactamente el caso que la metodología de concurrencia del propio Skill pide diferenciar: un check-then-act sin lock es una carrera real solo si algún camino de código puede producirla con los datos actuales — acá no puede, por la misma razón que el fix ya aplicado en `RescheduleReservationUseCase` (siempre se deriva un dato fresco antes del punto conflictivo). Sigue siendo, sin embargo, una promesa de esquema documentada y nunca cumplida, y una ausencia de red de seguridad si en el futuro se agrega un endpoint que sí vincule owners existentes.
- **Recomendación:** Si se agrega alguna vez un endpoint "vincular owner existente a club por ID", ese es el momento de agregar `PRIMARY KEY (owner_id, club_id)` (previa limpieza de duplicados si los hubiera) y replicar el patrón de lock ya usado en `Book`/`Reschedule`. Hasta entonces, esto es limpieza de deuda documental — corregir el comentario de `V1` o cerrar la promesa con una migración, lo que sea más simple, para que no quede una afirmación falsa en el schema versionado.

### F4 — 🟡 Medium — Las mutaciones de Schedule no replican el lock pesimista usado en Book/Reschedule/Delete; la query con lock pensada para esto existe pero nunca se llama

- **Confianza:** Confirmado (mecanismo y código muerto) / alcance de la carrera: real pero acotado a una ventana de timing específica
- **Evidencia:** `UpdateScheduleUseCase.java:40-49` y `DeleteScheduleUseCase.java:26` verifican reservas activas antes de mutar la agenda, sin ningún lock. `ReservationRepository.java:47-55` define `findActiveByCourtAndDay` con `@Lock(PESSIMISTIC_WRITE)` y filtro de día en la query; `ReservationEntity.java:19-20` documenta el índice `idx_reservation_court_day_status` como *"Cubre findActiveByCourtAndDay (verificación de disponibilidad + pessimistic lock)"*. Pero `ReservationRepositoryAdapter.findActiveByCourtAndDay()` (línea 70-76) en realidad invoca `reservationRepository.findActiveByCourt()` (sin lock, sin filtro de día — el filtro de día se hace después, en un `.filter()` de Java, línea 74). La query con lock (`findActiveByCourtAndDay` del repositorio) no tiene ningún caller.
- **Impacto:** Secuencia concreta: un Owner reduce el horario de una cancha (`UpdateScheduleUseCase`) mientras un Player reserva un turno válido bajo el horario todavía vigente (`BookReservationUseCase`), sin que ninguna de las dos transacciones bloquee a la otra (tocan tablas/filas distintas). Resultado posible: una reserva `RESERVADO` queda fuera del horario comercial recién guardado. No es doble-reserva ni corrupción de datos — es una inconsistencia de negocio silenciosa. El mismo problema aplica a `DeleteScheduleUseCase` contra una reserva creada entre el chequeo y el borrado de la franja.
- **Explicación:** El patrón correcto (lock pesimista como primera operación, antes de cualquier chequeo de disponibilidad) está probado y funciona en 4 casos de uso de este mismo proyecto; acá el índice y la query dan evidencia de que se pensó aplicar el mismo patrón, pero el adapter terminó usando la query equivocada.
- **Recomendación:** Cambiar `ReservationRepositoryAdapter.findActiveByCourtAndDay()` para invocar la query que ya existe (`reservationRepository.findActiveByCourtAndDay`, con lock y filtro de día) en vez de `findActiveByCourt`. Esto además resuelve la ineficiencia de traer reservas de todos los días (ver Performance). Si el lock recae sobre `reservation` y no sobre `court`, evaluar si conviene tomarlo sobre `Court` en su lugar (como hacen `Book`/`Reschedule`) para que ambos tipos de transacción compitan por el mismo recurso.

### F5 — 🔵 Low — Unicidad de nombre (branch-por-club, court-por-branch-y-deporte, sport global) validada solo en aplicación, sin respaldo de base

- **Confianza:** Confirmado
- **Evidencia:** `CreateBranchUseCase.java:26` (`existsByNameIgnoreCaseAndClub`), `CreateCourtUseCase.java:30` (`existsByNameAndBranchAndSport`), `CreateSportUseCase.java:19` (`existsByNameIgnoreCase`) — los tres check-then-act, sin lock. Ninguna de las tablas (`branches`, `court`, `sport`) tiene un `UNIQUE KEY` que cubra estas combinaciones en `V1__baseline.sql`.
- **Impacto:** Dos creaciones concurrentes con el mismo nombre (mismo club/branch/deporte según el caso) pueden producir dos filas duplicadas en contenido. Bajo impacto real: no es una identidad legal como F3, es una duplicación de nombre visible que confunde búsquedas por nombre pero no rompe ninguna otra invariante.
- **Recomendación:** Opcional — si se decide cerrar esta familia de gaps junto con F3, agregar `UNIQUE (club_id, name)` en `branches`, `UNIQUE (branch_id, sport_id, name)` en `court`, y `UNIQUE (name_sport)` en `sport`, en una única migración. Verificar antes que no haya duplicados ya existentes.

### F6 — 🔵 Low — 8 métodos de consulta muertos en `CourtRepository`/`ClubRepository`, uno de ellos el antipatrón `JOIN FETCH` de colección + `Pageable`

- **Confianza:** Confirmado
- **Evidencia:** ver tabla completa en Database & Indexes. `CourtRepository.findByNameAndBranchIdAndSportId`, `.findByIdAndBranch_Id`, `.findByBranch_VerificationStatusAndBranch_ActiveStatus` (dos sobrecargas), `.findByCourtWithSchedule`, `.findBySport_Id`, `.findByBranch_Id(Pageable)`; `ClubRepository.findApprovedWithEagerLoading`, `.findApprovedPaginated`. Comentario obsoleto en `ClubEntity.java:24` que referencia dos de estos métodos como si cubrieran "todas las queries de listado".
- **Impacto:** Ninguno en runtime — no se ejecutan nunca. Costo real: ruido de mantenimiento (alguien podría reactivar la versión con el antipatrón pensando que es la "más completa" por traer más relaciones) y documentación (el comentario de `ClubEntity`) que ya no describe el código actual.
- **Recomendación:** Eliminar los 8 métodos y corregir/eliminar el comentario de `ClubEntity.java:24`.

### F7 — 🔵 Low — `ErrorResponse.validationErrors` nunca se puebla; los errores de campo solo llegan concatenados como texto dentro de `message`

- **Confianza:** Confirmado
- **Evidencia:** `GlobalExceptionHandler.java:59-71` construye un `Map<String,String> validationErrors` local y lo usa únicamente vía `.toString()` dentro del string de `message` (línea 70); el campo `ErrorResponse.validationErrors` (`ErrorResponse.java`, campo declarado) nunca se asigna — el `buildErrorResponse` de esta rama no lo pasa al builder.
- **Impacto:** Un cliente que intente leer `response.validationErrors.campo` para mostrar el error junto al input correspondiente encuentra `null` siempre, y en su lugar tiene que parsear un `Map.toString()` de Java (`{campo1=mensaje1, campo2=mensaje2}`) embebido en un string libre — frágil y no es JSON real.
- **Recomendación:** Pasar el `validationErrors` ya construido al `ErrorResponse.builder().validationErrors(validationErrors)` y usar un `message` genérico ("Error de validación") separado del detalle por campo.

### F8 — 🔵 Low — Dos listados admin sin paginar

- **Confianza:** Confirmado
- **Evidencia:** `GetAllOwnersUseCase.java:18` (`OwnerRepositoryPort.findAll()` → `List<Owner>`), `GetAllCourtsUseCase.java` (`CourtRepositoryPort.findAll()` → `List<Court>`). Ambos expuestos solo a Admin.
- **Impacto:** Bajo con el volumen actual; crece con la cantidad de owners/canchas del sistema.
- **Recomendación:** Opcional — extender el mismo mecanismo `PageRequest`/`PageResult` ya usado en el resto de los listados, cuando el volumen lo justifique.

### F9 — 🔵 Low — Historial de reservas del jugador sin orden explícito

- **Confianza:** Confirmado
- **Evidencia:** `ReservationRepository.findByPlayer_Id(Long playerId)` — derived query sin `OrderBy`.
- **Impacto:** El orden de la lista devuelta por `GET /api/reservations/player/{playerId}` no está garantizado entre llamadas.
- **Recomendación:** `findByPlayer_IdOrderByDayDescStartTimeDesc` o equivalente explícito.

### F10 — 🔵 Low — Dependencia `spring-boot-starter-oauth2-client` sin ningún uso

- **Confianza:** Confirmado
- **Evidencia:** `pom.xml` la declara; `grep -rn "oauth2\|OAuth2" src/main/java` no encuentra ninguna referencia.
- **Impacto:** Superficie de dependencias innecesaria (tamaño del artefacto, superficie de CVEs de una librería no usada).
- **Recomendación:** Eliminarla del `pom.xml` si no hay un plan concreto de usar OAuth2 pronto.

### F11 — 🔵 Low — Cero tests unitarios directos para el código de autenticación (16% de cobertura de instrucciones), justo alrededor del único hallazgo Alto

- **Confianza:** Confirmado
- **Evidencia:** `target/site/jacoco/com.deportlink.deportlink.security.service/index.html` reporta 16% de instrucciones / 0% de ramas cubiertas para el paquete que contiene `JwtFilter`, `LoginAttemptService`, `AuthService`, `UserDetailsServiceImpl`. Búsqueda de archivos de test para esas cuatro clases: ninguno existe en `src/test/java`.
- **Impacto:** La lógica de umbral de `LoginAttemptService` (5 intentos, 15 minutos) y el filtro que valida cada request autenticado no tienen ningún test unitario directo — solo lo que incidentalmente ejerciten los tests de controller/integración.
- **Recomendación:** Agregar tests unitarios directos a `LoginAttemptService` (no requiere contexto de Spring) y a `JwtUtil`/`JwtFilter` (mockeando `UserDetailsService`). Es barato y cierra el hueco de testing más cercano al hallazgo de mayor severidad del informe.

### F12 — 🔵 Low — Quinto estilo de test no documentado; test mal nombrado y mal ubicado

- **Confianza:** Confirmado
- **Evidencia:** paquete `service/` (`ClubServiceTest.java`, `PlayerServiceTest.java`, `CourtServiceTest.java`, `ReservationServiceTest.java`, `CascadeDeletionRegressionTest.java`) con `@SpringBootTest` de integración completa, no mencionado en la tabla de "4 niveles" del README. `OwnerServiceTest.java` está en la raíz de `src/test/java/com/deportlink/deportlink/` y prueba `DateUtils.isOfLegalAge()`, no ningún `OwnerService`.
- **Impacto:** Dificulta que alguien nuevo ubique qué prueba qué guiándose por el README o la convención de paquetes.
- **Recomendación:** Documentar el quinto nivel en el README, o fusionarlo con uno existente si se decide que es redundante; mover `OwnerServiceTest` a `domain/` o `utils/` y renombrarlo.

### F13 — 🔵 Optional — `ReservationEntity.cancel()`: lógica de negocio muerta en una entidad JPA

- **Confianza:** Confirmado
- **Evidencia:** `ReservationEntity.java:60-71` define `cancel()` validando transición de estado y ventana — la misma regla que ya vive correctamente en `domain/model/Reservation.cancel()` (la que sí usa `CancelReservationUseCase`). Sin ningún caller.
- **Impacto:** Ninguno en runtime; contradice el principio ya documentado de "entidades JPA sin lógica de negocio" y puede confundir a quien lea el código.
- **Recomendación:** Eliminarlo (la excepción que usa, `ReservationNotUpdateException`, se mantiene — sigue viva en `UpdateScheduleUseCase.java:49`).
- **Nota de comparación:** ya reportado en `docs/audit-2026-08-25.md`; re-verificado de forma independiente en esta pasada, sigue siendo cierto.

### F14 — 🔵 Low — Naming inconsistente `desactivate` vs `deactivate` en rutas

- **Confianza:** Confirmado
- **Evidencia:** `BranchOwnerController.java:79` expone `PATCH /branches/owner/{idBranch}/desactivate` (con "s"); `CourtOwnerController.java:103` y `ClubOwnerController.java:108` usan `.../deactivate`.
- **Impacto:** Ninguno funcional; inconsistencia de API pública.
- **Recomendación:** Cambiarlo es un breaking change menor para cualquier cliente ya integrado — aceptar ambas rutas por un tiempo (deprecación) en vez de un rename directo.

### F15 — 🔵 Low — Sin Actuator/health-check ni configuración explícita de logging

- **Confianza:** Confirmado
- **Evidencia:** `pom.xml` no incluye `spring-boot-starter-actuator`; ningún `application*.properties` fija niveles de log ni existe `logback-spring.xml`.
- **Impacto:** Bajo para una instancia única sin SLA; sería lo primero que falta si se agrega monitoreo real.
- **Recomendación:** Agregar Actuator (`/health`, `/info`) es de bajo costo y alto valor cuando se decida operar esto más allá de un portfolio.

---

## Audit Validation

- **F1 — Confirmed**, severidad sin cambios. Re-trazado desde cero: `AuthController` → `AuthService` → `LoginAttemptService`, sin ningún manejo de proxy en el repo. Coincide con `docs/audit-2026-08-25.md` y con `docs/software-review-2026-08-26.md`, alcanzado de forma independiente.
- **F2 — Downgraded** respecto a la hipótesis inicial de "carrera de duplicados en `owner_club`": el gap de esquema y la promesa de migración incumplida son Confirmados, pero la carrera concreta no es alcanzable con el código actual (único caller siempre genera un `ownerId` fresco). Bajada de lo que hubiera sido un hallazgo Medium/High a Low, clasificado explícitamente como preventivo/defensa-en-profundidad, no como bug activo. Esta es la instancia concreta que la metodología del Skill pedía buscar antes de escribir el hallazgo — se hizo en Fase 1, no como corrección posterior.
- **F3 — Confirmed**, Medium. Re-verificados ambos callers (`RegisterOwnerUseCase` y `OwnerGatewayAdapter.register()`, este último invocado por `AddOwnerToClubUseCase`) — ambos alcanzables vía HTTP real, ambos con la misma falta de respaldo de base.
- **F4 — Confirmed** el mecanismo y el código muerto (la query con lock sin caller). La severidad se mantiene en Medium en vez de subir a High porque el peor resultado confirmado es una reserva fuera de horario, no una doble-reserva ni pérdida de datos, y porque activarla requiere una coincidencia de timing específica sobre una acción de Owner que en la práctica es infrecuente (achicar el horario de una cancha).
- **F5 — Confirmed**, severidad Low sostenida: impacto acotado a duplicación de contenido visible, no de identidad.
- **F6 — Confirmed** con conteo de referencias real (no solo lectura visual) para los 8 métodos.
- **F7 a F15 — Confirmed**, sin cambios de severidad respecto a como se escribieron en Fase 1 — cada uno se verificó releyendo el archivo citado, no la descripción propia del hallazgo.
- Ítems ya reportados en auditorías previas que esta pasada re-verificó independientemente y confirma sin cambios (no reformulados con full template porque no aportan nada nuevo): rate limiting en memoria (limitación ya documentada), JWT secret literal en `application-test.properties` (impacto nulo, ya evaluado), `Address` compartido entre `Branch`/`Player` (decisión ya documentada), mapeo manual en 4 adapters (decisión ya documentada), N+1 deliberado en paginado de clubes, `findNearby` sin bounding box.

## New Findings

Ninguno adicional a los ya listados en Findings — F2 a F6, F7, F9, F11, F12 y F14 son hallazgos que ninguna de las tres auditorías previas (`audit-2026-08-25.md`, `audit-database-2026-08-26.md`, `software-review-2026-08-26.md`) había reportado con este nivel de detalle, según la comparación hecha al final de esta pasada. En particular:

- La familia completa "identidad validada solo en aplicación, sin respaldo de base" (F2, F3, F5) surge de aplicar el mismo chequeo sistemático (toda columna con semántica de identidad real, no solo la primera que se ve sospechosa) a `owners.dni`, `owner_club`, `branches.name`, `court.name` y `sport.name_sport` — el Data Integrity deep-dive del Skill pide exactamente este barrido.
- F4 (el lock de Schedule) surgió de correlacionar un comentario de índice (`ReservationEntity.java:19-20`) con el método real invocado por el adapter — un ejemplo directo de "leer las migraciones/comentarios contra lo que el código realmente ejecuta".
- F6 surgió de contar referencias reales por método en vez de asumir que un método declarado en un repositorio está en uso.

No encontrar hallazgos "nuevos" fuera de los ya escritos en Fase 1 no es autocomplacencia en este caso: la Fase 2 sí modificó materialmente uno de ellos (F2, de una carrera activa hipotética a un hallazgo de defensa-en-profundidad), que es precisamente el tipo de ajuste que demuestra que la validación no fue mecánica.

## Final Assessment

**Vara usada:** proyecto de portfolio/aprendizaje en etapa temprana, instancia única, sin tráfico de producción real — no la misma vara que un sistema con usuarios activos y SLA. Con esa vara, DeportLink está en buen estado: cero hallazgos Críticos, un único hallazgo Alto que depende de una pregunta externa (topología de Railway) para dejar de serlo, y ningún hallazgo Medium que involucre pérdida de datos o corrupción — el peor escenario confirmado (F4) es una reserva que queda fuera de un horario recién editado, y el segundo (F3) es un duplicado de identidad que requiere una coincidencia de timing entre dos registros administrativos.

El patrón más útil que deja esta pasada: el proyecto sabe hacer bien la concurrencia difícil (lock pesimista + constraint de base, probado con Testcontainers) cuando el caso de uso es el central del negocio (reservar una cancha) — pero ese mismo cuidado no se extendió de forma pareja a invariantes de identidad estructuralmente idénticas (DNI de Owner, nombres únicos, agenda vs. reservas). Ninguna de ellas es tan grave como una doble reserva sería, y por eso el informe no las sube por encima de Medium/Low — pero es la misma familia de problema, y el código ya tiene el vocabulario (`findByIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`, columnas generadas + `UNIQUE`) para resolverlas con el mismo patrón si se decide.

**Qué priorizar primero, en orden:**
1. Confirmar la topología de red de Railway y resolver F1 en consecuencia — es la única pieza que puede convertir un hallazgo Potencial en uno Confirmado y elevar su urgencia real.
2. `UNIQUE KEY uq_owners_dni` (F3) — la corrección más barata con la semántica más seria (identidad legal duplicada) de todo el informe.
3. Redirigir `ReservationRepositoryAdapter.findActiveByCourtAndDay()` a la query que ya existe con lock (F4) — el código ya está escrito, es cambiar una llamada.
4. Tests unitarios directos para `LoginAttemptService`/`JwtUtil`/`JwtFilter` (F11) — barato, y cierra el hueco de testing alrededor de F1.
5. El resto (F2, F5, F6, F7, F8, F9, F10, F12, F13, F14, F15) son mejoras genuinas de bajo riesgo si se posponen; F2 en particular no necesita apuro porque hoy no es explotable.

**Lo que esta revisión no pudo verificar:**
- Si la suite de tests pasaría igual ejecutándola ahora mismo (los 239 tests / 0 fallos y los porcentajes de JaCoCo vienen de un reporte de una build anterior en `target/`, no de una ejecución en esta sesión — el código no cambió desde esa build, pero no es lo mismo que haberla corrido).
- Volumen real de datos en cualquier tabla — condiciona la urgencia real de F8, F9 y del comentario sobre `findNearby`.
- Topología de red real de Railway — condiciona la severidad real de F1.
- Si existen hoy filas duplicadas en `owner_club` o en `owners.dni` en una base ya desplegada.
- El motivo exacto de la cobertura baja en `mapper.dto`/`dto.request`/`dto.response` línea por línea (plausiblemente mapeo de campos sin lógica condicional, no lógica sin probar — pero no confirmado exhaustivamente).

**Sobre la comparación con los informes previos:** los tres informes ya presentes en el repo (`audit-2026-08-25.md`, `audit-database-2026-08-26.md`, `software-review-2026-08-26.md`) coinciden con esta pasada en el hallazgo Alto (F1) y en varios Low ya re-verificados aquí. Esta pasada, hecha con la restricción explícita de no abrir el informe del 26/08 durante el análisis, llega de forma independiente a una lectura más matizada de la familia de invariantes de identidad (distinguiendo cuál es explotable hoy — F3 — de cuál no lo es todavía — F2) y a un hallazgo de concurrencia (F4) centrado en un patrón que las pasadas anteriores no habían nombrado con este nivel de detalle mecánico (la query con lock que existe pero nunca se llama). Dónde el resultado pudo verse influido por haber visto de pasada el resumen del informe anterior antes de que se confirmara el alcance "desde cero": principalmente en saber de antemano que "unpaginated admin listing" y "owner_club" eran áreas a mirar — ambas se re-derivaron igual con evidencia propia en esta pasada, pero la sospecha inicial de dónde mirar no fue enteramente ciega.
