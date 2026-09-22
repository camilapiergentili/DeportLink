# Software Review

**Método:** `.claude/skills/software-review/SKILL.md` — revisión de solo lectura, independiente y desde cero (no se copió contenido de auditorías previas; cada hallazgo fue re-derivado leyendo el código en esta pasada).
**Rama/commit auditado:** `docs/audit-2026-08-25` @ `a5d0cfe` (HEAD actual — sin commits de código nuevos desde las auditorías previas).
**Relación con auditorías previas:** el repo ya tiene `docs/audit-2026-08-25.md` y `docs/audit-database-2026-08-26.md`, y una versión anterior de este mismo informe. Esta pasada es una auditoría **independiente**, no una actualización incremental: se releyó el código fuente directamente para cada área, sin asumir como ciertos los hallazgos previos. El resultado coincide con la enorme mayoría de lo ya documentado (se nota explícitamente dónde), y agrega evidencia nueva que las pasadas anteriores no tenían disponible (resultados reales de test suite y cobertura JaCoCo, leídos de artefactos de build existentes en `target/`) más dos hallazgos que no estaban reportados antes.
**Contexto del proyecto (para calibrar severidad):** proyecto individual de portfolio/aprendizaje (autora: Técnica en Programación), desplegado en Railway como instancia única, sin tráfico de producción real. Se usa como vara a lo largo de todo el informe.

---

## Executive Summary

| Severidad | Confirmado | Riesgo potencial | Mejora opcional |
|---|---|---|---|
| 🔴 Critical | 0 | 0 | 0 |
| 🟠 High | 0 | 1 | 0 |
| 🟡 Medium | 5 | 0 | 0 |
| 🔵 Low | 10 | 0 | 4 |

**Lectura general:** DeportLink es un backend chico (241 archivos de producción, ninguno supera 167 líneas) con una arquitectura hexagonal aplicada de forma consistente — no encontré ningún caso de lógica de negocio filtrada a un controller o a un adapter. Dos prácticas están genuinamente por encima del promedio para el tamaño del proyecto: los tests de concurrencia corren contra MySQL real vía Testcontainers (no contra H2 ni mocks) para la garantía anti-doble-reserva, y pude confirmar con evidencia real (no solo lectura de código) que la suite pasa completa — **239 tests, 0 fallos** — leyendo los reportes de Surefire ya presentes en `target/surefire-reports/`.

Esa misma evidencia (el reporte JaCoCo en `target/site/jacoco/`) también reveló algo que ninguna auditoría anterior de este repo había detectado: **la capa de autenticación (`JwtFilter`, `AuthService`, `LoginAttemptService`, `UserDetailsServiceImpl`) tiene cobertura de línea cercana a cero y ningún archivo de test dedicado** — un hueco de testing real en, precisamente, el componente (`LoginAttemptService`) que es el sujeto del único hallazgo 🟠 High de este informe.

El patrón que más se repite en los hallazgos no es "código roto" sino **una solución correcta aplicada en un lugar y no replicada en otro estructuralmente idéntico**: el lock pesimista + constraint única que protege `reservation` no se replicó para `owner_club`; la política de autorización "ADMIN o dueño" está bien resuelta pero copiada como texto SpEL en ~30 sitios en vez de centralizada; y — hallazgo nuevo de esta pasada — la paginación que ya se aplicó a clubes **no se aplicó ni a canchas ni a dueños**, dos listados admin globales, no solo uno.

---

## Architecture

**Evaluación de la elección arquitectónica, primero:** una arquitectura hexagonal completa es más de lo que un proyecto de un solo desarrollador estrictamente necesita para escalar, pero el README documenta explícitamente que Ports & Adapters es un objetivo de aprendizaje del proyecto, y confirmé que está **ejecutada consistentemente**, no a medias: no encontré ningún controller con lógica de negocio, ningún adapter con reglas de dominio, ninguna entidad JPA con invariantes propias que no fueran vestigiales (ver más abajo). Es una elección deliberada y bien ejecutada — no se reporta como hallazgo.

- **Fortaleza confirmada — dos familias de puertos con propósitos genuinamente distintos:** `domain/port/out/CourtRepositoryPort` (CRUD completo del agregado `Court`) y `application/port/out/CourtGateway` (una vista de solo lectura, `CourtSnapshot`, para lo que `BookReservationUseCase` necesita — confirmé en `BookReservationUseCase.java:43` que efectivamente solo usa `courtGateway.findByIdForUpdate`, no el repositorio de dominio completo). No es duplicación: es que el cliente (`BookReservationUseCase`) define su propia interfaz mínima en vez de depender del agregado completo. Mismo patrón replicado en `OwnerGateway`, `PlayerGateway`, `ScheduleGateway`.
- 🔵 **Low (Confirmado) — `ReservationEntity.cancel()` (`model/entity/ReservationEntity.java:60-71`) es lógica de negocio en una entidad JPA, sin ningún invocador.** Verifiqué con `grep` de `.cancel()` en todo `src/main/java`: la única llamada real de cancelación es `domain/model/Reservation.cancel()`, consumida por `CancelReservationUseCase`. El método en la entidad contradice el propio principio documentado ("entidades JPA sin lógica de negocio") y duplica, con validación ligeramente distinta, la regla real. Impacto: cero en runtime, solo puede confundir a quien lea el código pensando que ese es el camino real. Recomendación: eliminarlo (y su import de `ReservationNotUpdateException`, que sigue en uso en otro lado — `UpdateScheduleUseCase.java:49` — así que la excepción se mantiene).
- 🔵 **Low (Confirmado) — el modelo `Address` (`domain/model/Address.java`) se comparte entre `Branch` (relación 1:1) y `Player` (colección con `isDefault`, que no tiene sentido para una sucursal).** Son dos usos genuinamente distintos sobre el mismo record de dominio. Documentado ya como limitación conocida en el propio README — no es un hallazgo nuevo, solo re-verificado.

---

## SOLID

- **Fortaleza confirmada — Open/Closed en el manejo de excepciones:** cada subclase de `BusinessException` declara su propio `HttpStatus` en el constructor; `GlobalExceptionHandler` tiene un único `@ExceptionHandler(BusinessException.class)` (`exception/handler/GlobalExceptionHandler.java:31-39`) que no necesita tocarse al agregar una excepción nueva. El propio comentario del handler documenta el bug concreto que este diseño reemplazó (excepciones cayendo silenciosamente en 500 bajo el enfoque anterior de listas `@ExceptionHandler({...})`) — es una mejora medible, no solo estética.
- 🟡 **Medium (Confirmado) — la política de autorización "ADMIN o dueño" no está centralizada; vive copiada como expresión SpEL.** Desarrollo completo en **DRY & Code Smells** (mismo hallazgo, no se duplica el detalle acá). Relevante para SOLID porque es también una violación de Open/Closed: cambiar la regla de "ownership" implica editar ~30 literales de texto en vez de un único punto de extensión.
- Resto de las clases revisadas (use cases de una sola operación, adapters que implementan un puerto cada uno) siguen consistentemente el principio de responsabilidad única — sin más violaciones con consecuencia real.

---

## Clean Code

- 🟡 **Medium (Confirmado) — `ErrorResponse.validationErrors` existe pero nunca se completa; los errores de validación llegan al cliente incrustados como `Map.toString()` dentro de `message`.**
  - **Evidencia:** `exception/handler/ErrorResponse.java:23` declara `private Map<String, String> validationErrors;`. `GlobalExceptionHandler.handleValidationException` (`GlobalExceptionHandler.java:56-72`) arma ese mapa localmente (`validationErrors`) pero nunca lo pasa al builder de `ErrorResponse` — en cambio hace `"Error de validación: " + validationErrors.toString()` (línea 70) y lo mete en `message`. `buildErrorResponse` (línea 115-131) no recibe ni setea `validationErrors` en ningún `@ExceptionHandler` del archivo.
  - **Impacto:** un cliente que reciba un 400 de validación ve la sintaxis de `Map.toString()` de Java embebida en un string (`"Error de validación: {name=no puede estar vacío, price=debe ser positivo}"`) en vez de un objeto `validationErrors: {"name": "...", "price": "..."}` fácil de mapear campo a campo en un formulario de frontend. El campo pensado exactamente para eso queda siempre en `null`.
  - **Confianza:** Confirmado por lectura directa del único camino de código que produce esta respuesta.
  - **Recomendación:** en `handleValidationException`, pasar `.validationErrors(validationErrors)` al builder y dejar `message` como texto genérico. Cambio acotado a un método, bajo riesgo.
- 🔵 **Low (Confirmado) — comentarios de `JwtUtil` (`security/config/JwtUtil.java`) explican el "qué" en vez del "por qué", con un estilo distinto al resto del código.** Ejemplos literales: `//EL LIMPIADOR: Agarra el texto "Bearer eyJhbG..." y te devuelve solo el "eyJhbG..." limpio` (línea 53) y `// C. El extractor central (El que abre el paquete usando la llave secreta)` (línea 71) — restan información en vez de agregarla, ya que la línea de abajo es autoexplicativa. Sin impacto funcional; solo consistencia de estilo. Opcional: alinear con el resto del código si se toca el archivo por otro motivo.
- **Fortaleza confirmada — tamaño de archivo saludable:** conté líneas de todos los `.java` de producción; el más largo es `ClubRepositoryAdapter.java` con 167 líneas. No hay clases God ni métodos gigantes en ningún punto del código revisado.

---

## DRY & Code Smells

- 🟡 **Medium (Confirmado) — la expresión SpEL "ADMIN o dueño" está duplicada literalmente en ~30 anotaciones `@PreAuthorize` en vez de expresada una sola vez.**
  - **Evidencia:** conté 58 anotaciones `@PreAuthorize` en los 14 controllers (`grep` de `@PreAuthorize` sobre `controller/`). La forma "ADMIN o dueño" se repite casi verbatim en `BranchOwnerController` (5), `ClubOwnerController` (7), `CourtOwnerController` (7), `PlayerController` (2), `ScheduleController` (3), `OwnerController` (2) — con esta forma:
    ```java
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    ```
    Confirmé además que `CourtOwnerController.java` repite exactamente el mismo texto (`hasRole('ADMIN') or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))`) en `delete`, `update`, `updatePrice`, `activate` y `deactivate` — 5 veces dentro de un único archivo de ~110 líneas.
  - **Impacto:** es la política de autorización del sistema, copiada como texto en vez de expresada una vez. Si se necesita ajustar la regla (por ejemplo, agregar una condición de "y el club debe estar activo" a toda operación de escritura de un Owner), hay que tocar ~30 literales de forma idéntica; si se actualiza uno y se olvida otro, el resultado es una regla de autorización inconsistente entre endpoints estructuralmente idénticos — el tipo de bug de seguridad que es fácil introducir sin darse cuenta, porque cada ocurrencia individual se ve "correcta" aislada.
  - **Explicación:** Spring Security evalúa cada `@PreAuthorize` de forma independiente; nada fuerza que ocurrencias textualmente distintas se mantengan sincronizadas.
  - **Recomendación:** extraer la política a un punto reusable — una anotación compuesta propia (`@AdminOrOwnerOfCourt`) o un método en los beans `*Authorization` que encapsule "ADMIN o dueño" completo (`@courtAuthorization.canManage(#idCourt, authentication)`), en vez de repetir `hasRole('ADMIN') or (...)` en cada sitio. **Trade-off:** agrega un nivel de indirección para entender "quién puede hacer esto" — pero con ~30 copias ya existentes, el costo de esa indirección es menor que el riesgo de una copia desincronizada.
- 🔵 **Low (Confirmado) — 5 métodos de `CourtRepository` sin ningún llamador en `src/main/java`.** Verifiqué con `grep` de cada nombre de método contra todo el código de producción: `findByNameAndBranchIdAndSportId` (línea 21), `findByIdAndBranch_Id` (línea 22), `findByBranch_VerificationStatusAndBranch_ActiveStatus` (ambas variantes, `List` línea 23 y `Page` línea 51), `findByCourtWithSchedule` (línea 32), `findBySport_Id` (línea 34) — ninguno tiene invocador real (a diferencia de `findByBranch_IdAndActiveStatus`, que también existe en dos variantes `List`/`Page` pero **ambas sí están en uso**, confirmado en `CourtRepositoryAdapter.java:66,74`). El caso más notable es la variante `Page` de `findByBranch_VerificationStatusAndBranch_ActiveStatus`: hace `LEFT JOIN FETCH c.schedules` (colección `@OneToMany`) bajo `Pageable` — el antipatrón de paginación en memoria que el propio código evita deliberadamente en otro método del mismo archivo (comentario citando el warning de Hibernate `HHH90003004`). Al no tener llamador, hoy no ejecuta ese antipatrón contra la base — pero si se cablea a un caso de uso sin revisar el patrón, lo arrastra sin que nada lo señale.
  - **Recomendación:** eliminar los 4 métodos sin propósito aparente, o documentar por qué se mantienen. Para `findBySport_Id`: si se recupera como guard de `DeleteSportUseCase` (que hoy no verifica canchas asociadas antes de borrar un deporte — ver Data Integrity), no hace falta índice nuevo dado el volumen esperado de `sport`.
- 🔵 **Low (Confirmado) — mapeo manual dominio↔entidad repetido en 8 de 12 adapters de `infrastructure/adapter/`**, no solo en los 4 que documenta el README. Verificado con `grep` de `toDomain(`/`toEntity(`: además de los 4 documentados (`OwnerRepositoryAdapter`, `PlayerRepositoryAdapter`, `SportRepositoryAdapter`, `ScheduleRepositoryAdapter`), el mismo patrón aparece en `ClubRepositoryAdapter`, `BranchRepositoryAdapter`, `CourtRepositoryAdapter`, `ReservationRepositoryAdapter`. A diferencia de los 4 originales, estos manejan relaciones no triviales (`ClubRepositoryAdapter` reconcilia un `@ManyToMany` bidireccional comparando IDs actuales vs. nuevos) — MapStruct no resuelve eso de forma directa, así que la razón para no migrar es, si acaso, más fuerte acá. No amerita acción; es una actualización de la cifra documentada.
- 🔵 **Low (Confirmado) — naming inconsistente `desactivate` vs. `deactivate` en rutas.** `BranchOwnerController.java:79` expone `PATCH /{idBranch}/desactivate`; `ClubOwnerController.java:108` y `CourtOwnerController.java:103` usan `.../deactivate`. Ya documentado en el README como limitación conocida; sin impacto funcional, cambiarlo es un breaking change de API menor.

---

## Testing

- **Estructura confirmada por conteo propio (no solo lectura del README):** 36 archivos de test en `src/test/java`. Los 4 niveles documentados existen (`domain/` 2 archivos, `usecase/**` mayoría del resto, `controller/**` 8 con `@WebMvcTest`, concurrencia con Testcontainers), más un quinto paquete `service/` (5 archivos, `@SpringBootTest` contra H2) que el README no menciona como nivel formal.
- **Fortaleza confirmada — la garantía anti-doble-reserva se prueba contra MySQL real, no contra mocks.** `BookReservationConcurrencyTest`, `RescheduleReservationConcurrencyTest`, `BookReservationVsDeleteCourtConcurrencyTest`, `BookReservationVsDeleteBranchConcurrencyTest` y `ReservationUniqueSlotConstraintTest` usan Testcontainers con MySQL 8, justificado en el propio código: H2 no reproduce `PESSIMISTIC_WRITE`/`SELECT...FOR UPDATE` de InnoDB, y la columna generada de `V2` ni siquiera existiría en un esquema generado desde las entidades (`create-drop`). Decisión de testing más madura que el promedio para el tamaño del proyecto.
- **Evidencia nueva (no disponible en las auditorías anteriores) — leí los artefactos de build ya presentes en `target/`, sin ejecutar ningún test yo misma:**
  - `target/surefire-reports/*.txt` (35 archivos, de una corrida local fechada 25/08 08:52, código sin cambios desde entonces): sumando "Tests run"/"Failures"/"Errors" de los 35 reportes da **239 tests, 0 failures, 0 errors** — confirma exactamente la cifra que el README afirma, cosa que ninguna auditoría anterior había podido verificar (ambas decían explícitamente "no se ejecutó `mvn test`"). Sigue siendo un artefacto de una corrida pasada, no algo que yo haya regenerado — pero es evidencia real, no una repetición de la afirmación del README.
  - `target/site/jacoco/jacoco.csv` (mismo build): cobertura de línea agregada ≈ **48,9%** (1047/2142 líneas cubiertas). Desglosando por paquete encontré una brecha real: `com.deportlink.deportlink.security.service` (`AuthService`, `JwtFilter`, `LoginAttemptService`, `UserDetailsServiceImpl`) tiene **11% de cobertura de línea (6/53)**, y `security.resolver`/`security.dto` están en 14%/0%.
- 🟡 **Medium (Confirmado, nuevo) — la capa de autenticación no tiene ningún archivo de test dedicado, y su cobertura real es casi nula pese a ser el componente de seguridad más crítico del sistema.**
  - **Evidencia:** `grep` de `JwtFilter|LoginAttemptService|AuthService|JwtUtil|UserDetailsServiceImpl` sobre `src/test/java` solo encuentra referencias incidentales dentro de tests `@WebMvcTest` de controllers (que mockean la seguridad, no la ejercitan) — ningún `JwtFilterTest`, `AuthServiceTest`, `LoginAttemptServiceTest` ni `UserDetailsServiceImplTest` existe. El detalle de `jacoco.csv` por clase: `LoginAttemptService` 3/15 líneas cubiertas, `JwtFilter` 1/19, `AuthService` 1/14, `UserDetailsServiceImpl` 1/4.
  - **Impacto:** `LoginAttemptService` es exactamente la clase en el centro del único hallazgo 🟠 High de este informe (rate limiter de login) — su lógica de umbral (`MAX_ATTEMPTS = 5`), ventana de bloqueo (`LOCK_DURATION`) y la transición `registerFailure`/`isBlocked`/`registerSuccess` no tiene ni un test unitario que la ejercite directamente. Un cambio futuro en esa lógica (o en `JwtUtil`/`JwtFilter`, que validan y parsean el token en cada request autenticado) no tiene ninguna red de seguridad automatizada que lo detecte si rompe el comportamiento.
  - **Recomendación:** agregar tests unitarios directos para `LoginAttemptService` (umbral, expiración del bloqueo, reseteo en éxito) y para `JwtUtil`/`JwtFilter` (token expirado, token malformado, rol extraído correctamente) — son clases sin dependencias de Spring context pesadas, se prestan bien a JUnit puro con Mockito, en la misma línea que ya se hace para los use cases.
- 🟡 **Medium (Confirmado, nuevo) — cobertura de test del paquete `application.usecase.owner` es 19% y no existe ningún directorio `usecase/owner/` en los tests.** Verificado por `find`: `src/test/java/.../usecase/owner/` no existe; `RegisterOwnerUseCase` (valida mayoría de edad y unicidad de CUIL/DNI), `UpdateOwnerUseCase`, `DeleteOwnerUseCase` no tienen test dedicado. El paquete `usecase/schedule/` está en situación similar (20% de cobertura): de 5 casos de uso, solo `DeleteScheduleUseCase` tiene test propio — `AddScheduleUseCase`, `UpdateScheduleUseCase` (que aplica la ventana de cancelación configurable de `V3`) y los dos `Get*` no. Contrasta con `usecase/reservation` (100% de cobertura de línea) y `usecase/branch` (83%), que sí siguen el patrón "un test Mockito por caso de uso" de forma consistente.
  - **Impacto:** riesgo de regresión silenciosa específicamente en la validación de alta de un Owner (mayoría de edad, duplicados) y en la aplicación de la ventana de cancelación por sucursal — ambas son reglas de negocio con casos borde (fecha de nacimiento futura, ventana de 0 horas) que hoy dependen solo de los tests de dominio puro (`ReservationDomainTest`, `BranchDomainTest`) y de los tests de controller (que mockean el caso de uso, no ejercitan su lógica real).
  - **Recomendación:** priorizar tests Mockito para `RegisterOwnerUseCase` y `UpdateScheduleUseCase` primero (mayor superficie de reglas de negocio), siguiendo el mismo patrón ya usado en `usecase/branch`.
- 🔵 **Low (Confirmado, re-verificado) — el paquete `service/` (5 archivos `@SpringBootTest`) no está documentado como nivel formal en el README, y `OwnerServiceTest.java` está mal ubicado/nombrado** — verifiqué el archivo directamente: prueba `DateUtils.isOfLegalAge()`, no ningún `OwnerService` (que no existe como clase en el proyecto).
- 🔵 **Low (Confirmado, re-verificado) — ninguna prueba ejercita la carrera concurrente de `owner_club`.** Ver Concurrency.
- **No verificable en esta pasada:** si la suite pasaría igual si se corriera *ahora* (el reporte de Surefire leído es de una build anterior, no una ejecución mía); el motivo exacto detrás de la cobertura baja en `mapper.dto` (17%) y `dto.request` (34%) — plausiblemente clases con getters/setters/builders poco ejercitados directamente, no necesariamente lógica sin probar, pero no lo confirmé línea por línea.

---

## Security

- 🟠 **High (Riesgo potencial) — el rate limiter de login usa `getRemoteAddr()` sin ningún manejo de proxy.**
  - **Evidencia:** `AuthController.java:25` pasa `httpRequest.getRemoteAddr()` directamente a `authService.login(...)`; `LoginAttemptService` (ver arriba) indexa los intentos fallidos por esa IP en un `ConcurrentHashMap` local al proceso. `grep` de `X-Forwarded-For`, `ForwardedHeaderFilter`, `forward-headers-strategy`, `RemoteIpValve` sobre todo el proyecto (código y `.properties`) no encuentra nada — Tomcat embebido no confía en headers de proxy por defecto sin esa configuración explícita.
  - **Impacto:** si Railway antepone un proxy/load balancer HTTP a la instancia de la app (patrón típico en PaaS), `getRemoteAddr()` devolvería la IP interna del proxy, no la del cliente real — todos los logins fallidos legítimos y maliciosos compartirían la misma IP ante el rate limiter, así que 5 intentos fallidos de **cualquier usuario** bloquearían el login de **todos los usuarios** durante 15 minutos. Si Railway expone la IP real de forma transparente, el código funciona como está.
  - **Confianza:** Riesgo potencial — depende de la topología de red real de Railway, que no pude verificar leyendo el repositorio.
  - **Recomendación:** confirmar contra la documentación de Railway o inspeccionando un request real si hay un proxy de por medio; si lo hay, configurar `server.forward-headers-strategy=framework` (o leer `X-Forwarded-For` explícitamente validando el proxy de confianza).
- **Fortalezas confirmadas por lectura directa de código:**
  - **IDOR correcto en reservas:** `CancelReservationUseCase`/`RescheduleReservationUseCase` devuelven `ReservationNotFoundException` (404), no un 403, cuando un Player opera sobre la reserva de otro — el propio comentario del código explica que un 403 confirmaría que el id existe y es de otra persona.
  - **Tres beans de autorización que verifican ownership contra la base**, no contra el JWT: `CourtAuthorization.isOwnerOfCourt`, `BranchAuthorization.isOwnerOfBranch`, `ClubAuthorization.isOwnerOfClub`, cada uno respaldado por una query directa (`existsByCourtAndOwner`, etc., confirmado en `CourtRepository.java:111-123`) — un Owner no puede operar sobre recursos ajenos aunque adivine el id.
  - **Validación de arranque de CORS:** `SecurityConfig.validateCorsAllowedOrigins()` (línea 57-73) falla el contexto con `IllegalStateException` si `CORS_ALLOWED_ORIGINS` queda vacía — confirmado por lectura, no ejecuté la app para ver el abort real.
- 🔵 **Low (Confirmado) — dependencia `spring-boot-starter-oauth2-client` declarada en `pom.xml:30-33` sin ningún uso real.** `grep` case-insensitive de `oauth` sobre todo `src/main/java` y `src/main/resources` solo encuentra un falso positivo (la subcadena "oAuth" dentro de `DaoAuthenticationProvider`) — no hay configuración de cliente OAuth2, filtro, ni referencia real. Trae auto-configuración de Spring Security OAuth2 al classpath sin necesidad y puede confundir a quien lea `pom.xml` pensando que hay login social soportado. Recomendación: eliminarla si no hay plan concreto de usarla pronto.
- **Re-verificado sin cambios:** JWT sin refresh token ni revocación (`AuthController` solo expone `/login`); secreto de test hardcodeado en `application-test.properties` (alcance acotado a `src/test`, impacto nulo en producción — `application.properties` sí usa `${JWT_SECRET}` correctamente).

---

## Concurrency

- **Fortaleza confirmada — orden de lock correcto en el flujo de reservas.** Leí `BookReservationUseCase.execute()` línea por línea: `courtGateway.findByIdForUpdate(courtId)` (lock pesimista) es la **primera** operación de la transacción, antes de leer `findBookedSlots` — el comentario del propio código (línea 40-42) lo llama explícitamente "phantom read fix". Esto serializa a nivel de fila cualquier request concurrente para la misma cancha. Respaldado en profundidad por `uq_reservation_active_slot` (`V2__add_unique_reservation_slot.sql`) a nivel de base.
- 🟡 **Medium (Confirmado) — `Club.addOwner()` es un check-then-act puro en memoria, sin lock ni constraint de base, para la misma clase de invariante ("no duplicados") que sí está protegida por partida doble en `reservation`.**
  - **Evidencia:** `owner_club` (`V1__baseline.sql:66-73`) no tiene `PRIMARY KEY` ni `UNIQUE` sobre `(owner_id, club_id)` — el propio comentario de la migración documenta que se planeó agregar la constraint en una V2 que nunca llegó a escribirse (la V2 real, `V2__add_unique_reservation_slot.sql`, es sobre `reservation`). `Club.addOwner()` (`domain/model/Club.java:103-110`) valida `ownerIds.contains(ownerId)` sobre un `Set<Long>` cargado en memoria, sin ningún lock. `AddOwnerToClubUseCase.execute()` (línea 28) lee el club con `clubRepository.findById(clubId)` plano — no con una variante de actualización, a diferencia de `BookReservationUseCase`/`DeleteCourtUseCase`/`DeleteBranchUseCase`, que sí usan sus respectivas variantes `findByIdForUpdate`.
  - **Impacto:** dos altas concurrentes del mismo owner al mismo club pueden ambas leer el club antes de que la otra confirme, ambas pasan el chequeo `contains`, ambas insertan en `owner_club` — sin `PRIMARY KEY`/`UNIQUE`, ninguna falla. El impacto funcional inmediato está parcialmente amortiguado porque `ClubEntity.owners` es un `Set` del lado de lectura (dedupea en memoria al mapear a dominio), pero la tabla física queda con filas duplicadas.
  - **Sin test de este escenario** — confirmado (ver Testing).
  - **Recomendación:** agregar una migración que limpie duplicados existentes (si los hay — no verificable sin acceso a la base real) y agregue `PRIMARY KEY (owner_id, club_id)` a `owner_club`, replicando la receta ya usada y probada para `reservation`. Es el propio patrón de la aplicación, aplicado de forma incompleta a un segundo lugar que lo necesita igual.

---

## Database & Indexes

Ya existe un informe dedicado y exhaustivo para esta área: `docs/audit-database-2026-08-26.md`. Releí las migraciones, entidades y repositorios clave en esta pasada (no solo el informe) y confirmo su contenido sin discrepancias:

| Hallazgo | Severidad | Verificación en esta pasada |
|---|---|---|
| `owner_club` sin `PRIMARY KEY`/`UNIQUE` | 🟡 Medium | Re-verificado directamente en `V1__baseline.sql` (ver Concurrency) |
| 5 métodos muertos en `CourtRepository` | 🔵 Low | Re-verificado con `grep` propio (ver DRY & Code Smells) |
| `TicketEntity` sin `@UniqueConstraint` reflejando `uq_tickets_reservation_id` | 🔵 Low | Re-verificado: `TicketEntity.java:12-13` solo declara `@Table(name = "tickets")`, sin `uniqueConstraints`, a diferencia de `OwnerEntity` (que sí documenta `uq_owners_cuil`) |
| Índice `branches(verification_status, active_status)` sin `club_id` | 🔵 Opcional | Re-verificado — ninguna query viva lo necesitaría hoy |
| N+1 en paginado de clubes, documentado como trade-off deliberado | 🔵 Opcional | Re-verificado — el propio comentario en `ClubRepository.java` lo admite explícitamente |

**Fortalezas confirmadas de nuevo:** cada `@Index` en las entidades (`ReservationEntity`, `CourtEntity`, `BranchEntity`, `ClubEntity`) coincide exactamente con lo que crea `V1__baseline.sql`; `ddl-auto=validate` en el perfil default con Flyway como única fuente de verdad; la columna generada + `UNIQUE KEY` de `V2` es el workaround correcto para MySQL (sin índices únicos parciales nativos), con su propio test contra MySQL real porque H2 nunca la generaría desde el mapeo de entidades.

---

## Performance

- 🟡 **Medium (Confirmado, alcance ampliado respecto a lo ya documentado) — hay DOS listados administrativos globales sin paginar, no solo uno.**
  - **Evidencia:** `GetAllCourtsUseCase.execute()` (`GetAllCourtsUseCase.java:18-20`) hace `courtRepository.findAll()` sin `Pageable`, expuesto por `CourtAdminController`. Pero además, verificando cada `usecase/*/GetAll*UseCase` del proyecto con `grep` de `findAll()`, encontré que **`GetAllOwnersUseCase.execute()` (`GetAllOwnersUseCase.java:18-20`) tiene exactamente el mismo problema** — `ownerRepository.findAll()` sin paginar, expuesto en vivo por `OwnerController.getAll()` (`GET /api/owners`, solo Admin). Las auditorías previas de este repo reportaron esto como "el único" caso; no lo es.
  - Por contraste: `GetAllClubsUseCase` sí pagina (`clubRepository.findAll(pageRequest)` → `PageResult<Club>`), y `GetAllBranchesUseCase` está acotado por `clubId` (nunca es un listado global). `GetAllSportsUseCase` también usa `findAll()` sin paginar, pero **no lo cuento como el mismo tipo de hallazgo**: `sport` es una tabla de referencia de cardinalidad naturalmente acotada (deportes disponibles en la plataforma), no una entidad que crece con la actividad de usuarios — a diferencia de `court` (crece con cada sucursal nueva) y `owner` (crece con cada alta de cuenta).
  - **Impacto:** inofensivo con el volumen actual de canchas/dueños (esperable en esta etapa). Si cualquiera de las dos entidades crece a la par de la actividad de la plataforma, ambos endpoints se convierten en una traída completa de tabla sin límite, mismo mecanismo del hallazgo original pero duplicado.
  - **Recomendación:** alinear ambos casos de uso con el mismo patrón `PageRequest`/`PageResult` que ya usa `GetAllClubsUseCase`. No urgente al volumen actual, pero al ser dos ocurrencias del mismo patrón inconsistente, vale la pena resolverlas juntas en el mismo cambio.
- 🔵 **Low (Confirmado) — `BranchRepository.findNearby` calcula distancia haversine por fila con funciones trigonométricas (`acos`/`cos`/`sin`/`radians`) sin bounding-box previo ni índice espacial**, ordenando el resultado completo por esa distancia calculada (confirmado leyendo la query completa: filtro `WHERE` y `ORDER BY` repiten la misma fórmula de 6371·acos(...)). Sin volumen conocido de `branches`, no puedo afirmar que esto sea un problema real hoy — se marca explícitamente como no confirmable, tal como exige la Skill.
- 🔵 **Low (Confirmado) — `GetPlayerReservationsUseCase.execute()` trae todo el historial de un jugador sin paginar ni `ORDER BY` explícito.** `ReservationRepository.findByPlayerId` es un método derivado sin `Sort` ni `Pageable` — el orden de la respuesta al cliente no está garantizado. Con pocas reservas por jugador (esperable hoy) el único impacto es el orden no determinista; si el historial crece con los años, se vuelve una traída sin límite. Recomendación de bajo costo e independiente del volumen: agregar un `ORDER BY` explícito (ej. fecha descendente); paginar es una mejora condicionada al volumen real, no verificable en esta auditoría.
- **Sin caching en ningún nivel** — no se reporta como hallazgo: no hay evidencia de lecturas repetidas costosas que lo justifiquen hoy, y agregarlo sin ese motivo sería la sobre-ingeniería que la propia Skill pide evitar.

---

## Production Readiness

- 🔵 **Low (Confirmado) — no hay `spring-boot-starter-actuator`; no existe ningún endpoint de health check.** Verificado: `pom.xml` no lo declara, `grep` de `actuator` sobre todo el proyecto no encuentra nada. Impacto bajo hoy (instancia única en Railway, sin auto-scaling); se vuelve relevante si el despliegue empieza a depender de un healthcheck HTTP automatizado o si se agregan réplicas.
- 🔵 **Low (Confirmado) — sin configuración explícita de logging** (`logging.level.*`, salida a archivo, formato estructurado) en ningún `.properties` — corre con el logging por consola por defecto de Spring Boot. Aceptable para una instancia única cuyo log se lee del stream de Railway directamente; se vuelve limitación real si hace falta correlacionar logs entre instancias o en el tiempo.
- **Fortaleza confirmada — separación `ddl-auto`/perfiles con guardas explícitas.** `application.properties` (default) usa `validate` + Flyway; `application-dev.properties` usa `update` con advertencia explícita de no usarlo fuera de dev local. `SecurityConfig.validateCorsAllowedOrigins()` falla el arranque con mensaje claro si falta `CORS_ALLOWED_ORIGINS`, en vez de subir en un estado silenciosamente roto.
- **Re-verificado sin cambios:** secretos (`DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`) vía variables de entorno, sin defaults hardcodeados en el perfil default.
- **No verificable en esta auditoría:** no hay `Dockerfile` en el repo (`spring.docker.compose.enabled=false`, documentado en el propio README); cómo Railway construye y arranca la app en concreto no se puede confirmar leyendo el repositorio.

---

## Prioritized Findings

Ordenado por impacto real, no por sección de origen:

1. 🟠 **High (Riesgo potencial)** — Rate limiter de login usa `getRemoteAddr()` sin manejo de proxy. *(Security)*
2. 🟡 **Medium (Confirmado)** — `owner_club` sin `PRIMARY KEY`/`UNIQUE`; `Club.addOwner()` es check-then-act puro en memoria. *(Concurrency / Database)*
3. 🟡 **Medium (Confirmado)** — Capa de autenticación (`JwtFilter`, `AuthService`, `LoginAttemptService`, `UserDetailsServiceImpl`) sin ningún test dedicado y con ~11% de cobertura real — incluye la clase del hallazgo #1. *(Testing — nuevo)*
4. 🟡 **Medium (Confirmado)** — Política de autorización "ADMIN o dueño" duplicada como texto SpEL en ~30 sitios. *(DRY & Code Smells / SOLID)*
5. 🟡 **Medium (Confirmado)** — `ErrorResponse.validationErrors` nunca se completa; errores de validación llegan como `Map.toString()` embebido en `message`. *(Clean Code)*
6. 🟡 **Medium (Confirmado, alcance ampliado)** — Dos listados admin globales sin paginar (`GetAllCourtsUseCase` **y** `GetAllOwnersUseCase`), no uno solo. *(Performance / Database)*
7. 🟡 **Medium (Confirmado, nuevo)** — `usecase/owner` (0 tests dedicados, 19% cobertura) y la mayor parte de `usecase/schedule` (20%) sin tests unitarios, a diferencia de `reservation`/`branch`. *(Testing — nuevo)*
8. 🔵 **Low** — 5 métodos muertos en `CourtRepository`, uno con antipatrón `JOIN FETCH` + `Pageable`. *(DRY & Code Smells / Database)*
9. 🔵 **Low** — Dependencia `spring-boot-starter-oauth2-client` sin ningún uso real. *(Security)*
10. 🔵 **Low** — Sin `ORDER BY` explícito en `GetPlayerReservationsUseCase` (orden no determinista del historial). *(Performance)*
11. 🔵 **Low** — Sin Actuator/health check. *(Production Readiness)*
12. 🔵 **Low** — Sin configuración explícita de logging. *(Production Readiness)*
13. 🔵 **Low** — `ReservationEntity.cancel()` es lógica de negocio muerta en una entidad JPA. *(Architecture)*
14. 🔵 **Low** — `Address` compartido entre `Branch` y `Player` para dos usos distintos. *(Architecture)*
15. 🔵 **Low** — Comentarios de `JwtUtil` explican el "qué" en vez del "por qué". *(Clean Code)*
16. 🔵 **Low** — Naming inconsistente `desactivate` vs `deactivate` en rutas. *(DRY & Code Smells)*
17. 🔵 **Low** — Paquete de test `service/` no documentado como nivel formal; `OwnerServiceTest` mal nombrado/ubicado. *(Testing)*
18. 🔵 **Low** — Sin test de la carrera concurrente de `owner_club`. *(Testing)*
19. 🔵 **Low** — `TicketEntity` sin `@UniqueConstraint` documentando `uq_tickets_reservation_id`. *(Database)*
20. 🔵 Opcional — Mapeo manual dominio↔entidad en 8/12 adapters (no solo los 4 documentados). *(DRY & Code Smells)*
21. 🔵 Opcional — N+1 documentado y deliberado en paginado de clubes. *(Performance / Database)*
22. 🔵 Opcional — `findNearby` sin índice espacial/bounding-box; volumen no verificable. *(Performance)*

---

## Final Assessment

**Vara usada:** proyecto en etapa temprana/portfolio, instancia única, sin tráfico de producción real — no la misma vara que un sistema con usuarios activos y SLA. Con esa vara, DeportLink está en buen estado: cero hallazgos Críticos, y el único Alto es un riesgo potencial que depende de una sola pregunta externa (topología de red de Railway) para dejar de serlo. La mayoría de los hallazgos Medium son "el patrón correcto ya existe en el código, falta replicarlo en un segundo lugar" — no diseño roto.

Esta pasada, hecha de forma independiente y verificando directamente contra el código (no contra los informes previos), confirma la enorme mayoría de lo que las auditorías de `docs/audit-2026-08-25.md` y `docs/audit-database-2026-08-26.md` ya habían documentado, y agrega valor real en dos frentes: (1) evidencia concreta de test/cobertura leída de artefactos de build ya existentes, en vez de "no verificable"; y (2) dos hallazgos que las pasadas anteriores no habían reportado — un segundo listado admin sin paginar (`GetAllOwnersUseCase`) y un hueco de testing específico en la capa de autenticación, que resulta directamente relevante para el hallazgo de mayor severidad del informe.

**Qué priorizar primero, en orden:**
1. Confirmar la topología de red de Railway (proxy sí/no) y resolver el hallazgo Alto en consecuencia.
2. Agregar tests unitarios directos a `LoginAttemptService`/`JwtUtil`/`JwtFilter` — es barato (no requieren contexto de Spring pesado) y cierra el hueco de testing justo alrededor del único hallazgo Alto.
3. Cerrar la brecha de `owner_club` con la misma receta ya probada para `reservation` (limpieza + `PRIMARY KEY`).
4. Centralizar la política de autorización SpEL antes de que se agreguen más endpoints owner-scoped.
5. El resto (paginar `GetAllOwnersUseCase`/`GetAllCourtsUseCase`, completar `validationErrors`, tests de `usecase/owner`/`schedule`, limpieza de métodos muertos, Actuator/logging) son mejoras genuinas pero de bajo riesgo si se posponen.

**Lo que esta revisión no pudo verificar:**
- Si la suite de tests pasaría igual corriéndola ahora mismo (los 239 tests/0 fallos vienen de un reporte de Surefire de una build anterior en `target/`, no de una ejecución mía en esta sesión — aunque el código no cambió desde esa build).
- Volumen real de datos en cualquier tabla — condiciona directamente la urgencia de los hallazgos de Performance marcados como opcionales o de bajo volumen.
- Topología de red real de Railway (proxy sí/no) — condiciona la severidad real del único hallazgo Alto.
- Cómo Railway construye y arranca la app en ausencia de `Dockerfile`.
- Si existen hoy filas duplicadas en `owner_club` en la base real.
- El motivo exacto de la cobertura baja en `mapper.dto`/`dto.request` (17%/34%) — plausiblemente clases poco ejercitadas directamente más que lógica sin probar, pero no lo confirmé línea por línea.

**Preguntas abiertas para el equipo** (en este caso, la autora): las seis de arriba, más las ya abiertas en los informes previos (destino de los tests de `service/`, si vale la pena documentar un quinto nivel de testing en el README).
