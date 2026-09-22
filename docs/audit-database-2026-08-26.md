# Auditoría técnica DeportLink — Base de datos e índices — 2026-08-26

**Alcance:** Modelo de datos, índices, constraints, migraciones (Flyway) y su correlación con las queries reales de la aplicación (JPA/Hibernate). No incluye seguridad, arquitectura ni testing (ver `audit-2026-08-25.md` para eso).
**Rama/commit auditado:** `docs/audit-2026-08-25` @ `a5d0cfe`
**Método:** Solo lectura de código y migraciones. No se ejecutó la aplicación, ninguna migración, ni `EXPLAIN`/`EXPLAIN ANALYZE` contra una base real — donde se recomienda, se señala explícitamente que la validación con esas herramientas está pendiente.

## Resumen

| Severidad | Confirmado | Riesgo potencial | Mejora opcional |
|---|---|---|---|
| Alto | 0 | 0 | 0 |
| Medio | 1 | 1 | 0 |
| Bajo | 1 | 0 | 3 |

**Contexto general (positivo):** el diseño de índices existente no es genérico — cada `@Index` en las entidades (`ReservationEntity`, `CourtEntity`, `BranchEntity`, `ClubEntity`, `ScheduleEntity`) trae un comentario que nombra el método de repositorio exacto que cubre, y los nombres/columnas coinciden 1:1 con lo que realmente crea `V1__baseline.sql`. Bajo `spring.jpa.hibernate.ddl-auto=validate` (prod) esos índices no los crea Hibernate — los crea Flyway — así que la coincidencia importa: confirma que la documentación en las entidades describe el esquema real, no un esquema aspiracional. El caso más sofisticado es `V2__add_unique_reservation_slot.sql`: una columna generada (`active_slot_court_id`) más un `UNIQUE KEY` para lograr un "unique índex parcial" en MySQL (que no soporta `WHERE` en índices únicos), como defensa en profundidad detrás del lock pesimista de `BookReservationUseCase`/`RescheduleReservationUseCase` — y además existe un test dedicado (`ReservationUniqueSlotConstraintTest`, Testcontainers + MySQL real + Flyway habilitado) que verifica esa restricción exactamente porque los demás tests corren contra H2 con `ddl-auto=create-drop`, que jamás generaría esa columna calculada. Es el patrón correcto para probar una garantía que vive en SQL crudo y no en el mapeo de la entidad.

---

## Hallazgos

### [Medio] `owner_club` no tiene `PRIMARY KEY` ni `UNIQUE` — la unicidad dueño↔club depende solo de una comprobación en memoria

- **Categoría:** Integridad y concurrencia
- **Confianza:** Confirmado (ausencia de constraint) — riesgo de explotación bajo concurrencia real no verificado
- **Evidencia:**
  - `V1__baseline.sql:66-73` crea `owner_club` con las dos FK (`fk_owner_club_owner`, `fk_owner_club_club`) pero sin `PRIMARY KEY` ni `UNIQUE KEY` sobre `(owner_id, club_id)`. El propio comentario del archivo dice literalmente: *"Sin PRIMARY KEY: refleja el estado real de la DB. V2 agrega PRIMARY KEY (owner_id, club_id) previa limpieza de duplicados."* — pero `V2__add_unique_reservation_slot.sql` (la única V2 que existe) es sobre otra tabla completamente distinta (`reservation`). Esa limpieza nunca se hizo: la documentación interna del propio proyecto ya señalaba esta deuda y quedó sin resolver.
  - `OwnerEntity.java:31-36` mapea el lado propietario del `@ManyToMany` vía `@JoinTable(name = "owner_club", ...)` sin `uniqueConstraints`.
  - `Club.addOwner()` (`Club.java:103-110`) es la única barrera: `if (ownerIds.contains(ownerId)) throw new OwnerAlreadyExistsException(...)`, sobre un `Set<Long>` cargado al leer el club dentro de la misma transacción — es decir, un check-then-act en memoria de aplicación, sin `SELECT ... FOR UPDATE` sobre el club ni sobre `owner_club`.
  - `AddOwnerToClubUseCase.execute()` no aplica ningún lock pesimista al leer el club (`clubRepository.findById(clubId)` plano), a diferencia de `BookReservationUseCase`/`RescheduleReservationUseCase`/`DeleteCourtUseCase`/`DeleteBranchUseCase`, que sí usan `findByIdForUpdate`/`findByBranchIdForUpdate` exactamente para esta clase de problema.
  - No existe ningún test (`grep` de `owner_club`/`OwnerAlreadyExists` en `src/test/java` sin resultados) que ejercite dos altas concurrentes del mismo owner al mismo club.
- **Impacto:** Dos requests concurrentes de `POST .../owners` (o el flujo que invoque `AddOwnerToClubUseCase`) agregando el mismo owner al mismo club pueden leer el club antes de que la otra transacción haga commit, ambas ven `ownerIds` sin ese id, ambas insertan una fila en `owner_club` — sin `PRIMARY KEY`/`UNIQUE`, ninguna falla. Resultado: fila duplicada `(owner_id, club_id)` en la tabla de unión. El impacto funcional inmediato es acotado porque el lado `Set<OwnerEntity>` de `ClubEntity.owners` deduplica en memoria al leer, pero la tabla física queda con basura, y cualquier código futuro que cuente filas de `owner_club` directamente (en vez de pasar por el `Set` de Hibernate) contaría de más.
- **Recomendación:** Agregar una migración `V4` que primero elimine duplicados existentes (`DELETE` dejando una fila por `(owner_id, club_id)`, si los hay) y luego agregue `PRIMARY KEY (owner_id, club_id)` — tal como el propio comentario de `V1` ya anticipaba. Esto es defensa en profundidad de la misma familia que `uq_reservation_active_slot`: no reemplaza el check de `Club.addOwner()`, lo respalda para el caso de carrera. Antes de escribir la migración, correr una query de diagnóstico contra la base real para confirmar si ya existen duplicados hoy (`SELECT owner_id, club_id, COUNT(*) FROM owner_club GROUP BY owner_id, club_id HAVING COUNT(*) > 1`) — no se pudo ejecutar como parte de esta auditoría de solo lectura.

---

### [Medio] `GetAllCourtsUseCase` — único listado admin sin paginar en toda la aplicación; crece sin límite

- **Categoría:** Consultas costosas / consistencia con el resto del código
- **Confianza:** Riesgo potencial — depende del volumen real de canchas, no verificado
- **Evidencia:**
  - `GetAllCourtsUseCase.java:18-20` → `courtRepository.findAll()` (el `findAll()` sin `Pageable` de `JpaRepository`), expuesto por `CourtAdminController` como endpoint admin de listado global de canchas.
  - Por contraste, el resto de los listados administrativos equivalentes sí paginan: `GetAllClubsUseCase.java:18` usa `clubRepository.findAll(pageRequest)` (`PageResult<Club>`), y `GetAllBranchesUseCase` está acotado por `clubId` (`findAllByClub`, nunca global). `GetAllCourtsUseCase` es la única excepción a ese patrón — no por una razón de dominio distinta, sino, aparentemente, por no haberse actualizado junto con el resto durante la migración a paginación.
- **Impacto:** Con pocas canchas (esperable en esta etapa del producto) es inofensivo. Si la cantidad de canchas crece a la par de sucursales/clubes, este endpoint se convierte en una traída completa de tabla en cada llamada, sin límite — a diferencia de todos los demás listados globales de la aplicación.
- **Recomendación:** Alinear `GetAllCourtsUseCase`/`CourtAdminController` con el mismo patrón de `PageRequest`/`PageResult` que ya usa `GetAllClubsUseCase`, reutilizando `CourtRepositoryPort` (que ya expone `findApprovedPaginated` como precedente). No es urgente si el volumen actual de canchas es bajo — pero si se prioriza, es una razón más para no dejarlo para "cuando duela": es un cambio de firma de método (breaking para quien ya consuma el endpoint sin paginar).

---

### [Bajo] Cinco métodos de `CourtRepository` sin ningún llamador — código muerto que también invalida análisis de índices sobre ellos

- **Categoría:** Consistencia entre el modelo de datos y las consultas reales de la aplicación
- **Confianza:** Confirmado (búsqueda exhaustiva en `src/main/java`, sin resultados fuera de la propia declaración)
- **Evidencia:** `grep` de cada nombre contra todo `src/main/java` no encuentra invocación alguna, solo la declaración en `CourtRepository.java`:
  - `findByBranch_VerificationStatusAndBranch_ActiveStatus` (ambas variantes, `List` y `Page`, líneas 23 y 51) — este es el caso más notable: la variante `Page` hace `LEFT JOIN FETCH c.schedules` (colección `@OneToMany`) bajo `Pageable`, el mismo patrón que en otro lado del propio archivo (`ClubRepository.findByVerificationStatusAndActiveStatus`, comentario en línea 53-54) se evita deliberadamente citando el warning de Hibernate `HHH90003004` (paginación en memoria al traer una colección con `JOIN FETCH`). Como el método nunca se llama, hoy no ejecuta ese antipatrón contra la base — pero si algún día se cablea a un caso de uso, arrastra el problema sin que nada lo señale.
  - `findByCourtWithSchedule` (línea 32)
  - `findByNameAndBranchIdAndSportId` (línea 21)
  - `findByIdAndBranch_Id` (línea 22)
  - `findBySport_Id` (línea 34) — su nombre sugiere que pudo haber sido pensado como guard para `DeleteSportUseCase` (impedir borrar un deporte con canchas asociadas), pero ese caso de uso no lo llama ni tiene ningún guard equivalente: `DeleteSportUseCase.execute()` solo verifica que el deporte exista y borra. La única protección real hoy es la FK `fk_court_sport` (sin `ON DELETE CASCADE`, por lo tanto `RESTRICT` implícito de InnoDB) — la base rechazará el `DELETE` con una excepción de integridad si hay canchas usando ese deporte, pero sin el guard de aplicación el error que ve quien llama al endpoint es una `DataIntegrityViolationException` cruda, no una excepción de dominio como sí ocurre para `Court`/`Branch` (`hasReservations()`).
- **Impacto:** Ninguno en runtime. Ruido para el análisis de índices (invita a diagnosticar patrones de acceso que no ocurren) y para quien lea el repositorio buscando entender qué se consulta realmente.
- **Recomendación:** Si `findBySport_Id` se recupera para usarlo como guard de `DeleteSportUseCase` (dándole a esa clase el mismo patrón de excepción de dominio que ya tienen `Court`/`Branch`), no hace falta índice nuevo — `sport` es una tabla chica y `court.sport_id` no tiene volumen alto por deporte. Los demás cuatro métodos: eliminar si nadie los necesita, o documentar por qué se mantienen si son parte de un contrato de repositorio pensado a futuro.

---

### [Mejora opcional] Paginación de `ClubRepository.findByVerificationStatusAndActiveStatus`/`findApprovedPaginated` con N+1 documentado y aceptado explícitamente

- **Categoría:** Posible problema de N+1
- **Confianza:** Confirmado como diseño (el propio comentario del código lo admite); costo real no medido
- **Evidencia:** `ClubRepository.java:53-54` — *"Paginado limpio sin JOIN FETCH de colecciones — evita el warning HHH90003004. Los owners se cargan lazy dentro de la transacción del adapter al llamar toDomain()."* — y efectivamente, `ClubRepositoryAdapter.toDomain()` (línea 151-167) recorre `entity.getOwners()` por cada `ClubEntity` de la página, disparando un `SELECT` lazy por club listado (N+1 clásico, N = tamaño de página).
- **Impacto:** Es un trade-off consciente (evitar el antipatrón de paginación en memoria a cambio de aceptar N+1 acotado por el tamaño de página) documentado en el propio código — no es un descuido. El costo es N queries adicionales pequeñas (por PK de `owner_club`, indexado automáticamente por la FK) en vez de 1 query grande; con tamaños de página típicos (10-50) es Probablemente aceptable, pero no está medido.
- **Recomendación:** Si en algún momento se nota lentitud en el listado admin de clubes paginado, la alternativa estándar es un segundo `SELECT ... WHERE club_id IN (:ids)` sobre `owner_club`/`owners` después de traer la página de IDs (patrón "batch fetch" de 2 queries en vez de 1+N), no volver al `JOIN FETCH` con `Pageable`. Validar primero con `EXPLAIN ANALYZE`/logging de Hibernate (`hibernate.generate_statistics`) antes de tocar algo que ya fue una decisión deliberada.

---

### [Mejora opcional] Sin índice para filtrar `branches` por `(verification_status, active_status)` sin `club_id` — hoy no lo necesita ninguna query viva

- **Categoría:** Correlación código↔índices / índice potencialmente faltante
- **Confianza:** Riesgo potencial solo si se agrega una query de este tipo — hoy no aplica a ninguna consulta real
- **Evidencia:** El único índice sobre `branches` además de la PK es `idx_branches_club_verification_active (club_id, verification_status, active_status)` (`BranchEntity.java:17-20`, `V1__baseline.sql:86`). Toda query viva que filtra por `verification_status`/`active_status` en `BranchRepository` sí incluye `club_id` como filtro (`findActiveAndApprovedByClubId`) o filtra por otras columnas enteramente (`findApprovedBySport` vía join con `court.sport_id`, `findNearby` vía geolocalización, `findByNameContainingIgnoreCaseAndVerificationStatusAndActiveStatus` vía `LIKE`). La única query que sí necesitaría un filtro de sucursales aprobadas *sin* acotar por club es `CourtRepository.findApprovedClean` (usada por `GetApprovedCourtsUseCase`, el listado público de canchas), pero esa filtra `branch.verificationStatus`/`branch.activeStatus` como parte de un JOIN desde `court`, no como una query directa sobre `branches` — el optimizador puede apoyarse en `idx_court_branch_active (branch_id, active_status)` desde el lado de `court` sin necesitar un índice adicional en `branches`, dependiendo del plan que elija.
- **Impacto:** Ninguno confirmado hoy. Se documenta como observación, no como recomendación de acción: si en el futuro aparece un endpoint que liste sucursales aprobadas globalmente (análogo a `ClubRepository.idx_clubs_verification_active`, que sí existe para clubes), sería el momento de agregar el índice equivalente en `branches` — no antes.
- **Recomendación:** No crear el índice ahora sin evidencia de una query real que lo necesite (el propio criterio de esta auditoría). Si se agrega ese endpoint más adelante, validar con `EXPLAIN` si `findApprovedClean` realmente se beneficiaría de un índice adicional en `branches(verification_status, active_status)` antes de crearlo — el join siempre puede resolverse por el lado de `court`.

---

### [Mejora opcional] `TicketEntity` es la única entidad con una `UNIQUE` real en la base que no se refleja en el `@Table`

- **Categoría:** Consistencia entre migraciones y entidades
- **Confianza:** Confirmado; impacto: ninguno (bajo `ddl-auto=validate`, Hibernate no valida la ausencia de constraints no declaradas)
- **Evidencia:** `V1__baseline.sql:149` crea `UNIQUE KEY uq_tickets_reservation_id (reservation_id)` sobre `tickets`, reflejando la relación `@OneToOne` real (`ReservationEntity.ticket` ↔ `TicketEntity.reservation`). Sin embargo `TicketEntity.java` no declara `@Table(uniqueConstraints = ...)` para documentarlo, a diferencia de `OwnerEntity` (`uq_owners_cuil`) y `ClubEntity` (`uq_clubs_cuit`, `uq_clubs_legal_name`), que sí siguen esa convención.
- **Impacto:** Ninguno funcional. Es la única entidad que rompe el patrón de "todo `UNIQUE KEY` de la migración tiene su espejo documentado en `@Table(uniqueConstraints=...)` de la entidad", que es precisamente lo que permitió verificar en esta auditoría, sin ambigüedad, que las demás unicidades son reales y no solo aspiracionales.
- **Recomendación:** Agregar `@Table(name = "tickets", uniqueConstraints = @UniqueConstraint(name = "uq_tickets_reservation_id", columnNames = "reservation_id"))` a `TicketEntity` por consistencia documental. Cero impacto en runtime bajo `ddl-auto=validate`.

---

## Cobertura verificada (sin hallazgos — se documenta para no repetir el análisis)

- **`reservation`**: los tres índices (`idx_reservation_court_day_status`, `idx_reservation_court_status`, `idx_reservation_player`) cubren exactamente `findActiveByCourtAndDay` (con `@Lock(PESSIMISTIC_WRITE)`, la query más crítica del sistema), `findStartTimesByCourtAndDay`, `findActiveByCourt` y `findByPlayer_Id` — orden de columnas correcto (igualdad antes que rango) en los tres casos.
- **`court`**: `idx_court_branch_active` e `idx_court_branch_sport` cubren `findByBranch_IdAndActiveStatus` (paginado y no paginado, uso real confirmado) y `findByBranch_IdAndSport_Id`.
- **`availability`**: `idx_availability_court_day` cubre `findByCourtIdAndDay`, invocado en cada generación de turnos disponibles.
- **`clubs`**: `idx_clubs_verification_active` cubre los listados de clubes aprobados (`findApprovedWithEagerLoading`, `findApprovedPaginated`, `findByVerificationStatusAndActiveStatus`).
- **Ownership checks vía PK** (`existsByCourtAndOwner`, `existsByIdAndClub_Owners_Id`, `existsByIdAndOwners_Id`, etc.): todas arrancan el filtro desde una PK (`c.id = :idCourt`, etc.), por lo que el resto de los `JOIN`s de autorización son baratos independientemente de índices adicionales.
- **FKs sin índice explícito nombrado** (`address.id_player`, `owner_club.owner_id`, `owner_club.club_id`): InnoDB crea automáticamente un índice de soporte para cada columna con `FOREIGN KEY` que no esté ya cubierta por uno existente — no son índices "faltantes", solo no tienen un nombre custom en la migración. Confirmado por lectura de las restricciones `CONSTRAINT fk_...` en `V1__baseline.sql`; no verificado contra `SHOW INDEX` en una base real.
- **`ddl-auto` por entorno**: `application.properties` (prod) usa `validate` + Flyway con `baseline-on-migrate=true`/`baseline-version=1` — coherente con el comentario de `V1__baseline.sql` de que fue verificada contra `SHOW CREATE TABLE` real antes de trackearse. `application-dev.properties` usa `update` (con advertencia explícita en el propio archivo de no usarlo fuera de dev local). Tests usan H2 + `create-drop` con Flyway deshabilitado, excepto `ReservationUniqueSlotConstraintTest`, que corre contra MySQL real vía Testcontainers con Flyway habilitado específicamente porque la restricción que prueba no existe en el mapeo de la entidad. Ningún entorno usa `ddl-auto=create`/`update` contra una base que Flyway también gestione — no hay riesgo de que ambos mecanismos compitan por el esquema.

## Fuera de lo verificable en esta auditoría

- **Volumen real de datos** en cualquier tabla (`reservation`, `owner_club`, `court`, etc.) — todas las recomendaciones marcadas "mejora opcional" o condicionadas a volumen se basan en el diseño del código, no en métricas de producción.
- **Planes de ejecución reales** (`EXPLAIN`/`EXPLAIN ANALYZE`) para ninguna de las queries listadas — todo el análisis de qué índice cubre qué query es por lectura de columnas/predicados, no por plan verificado.
- **Presencia real de filas duplicadas hoy en `owner_club`** — la recomendación de limpieza previa a agregar la `PRIMARY KEY` asume que podría haberlas, pero no se consultó una base real.
- **Comportamiento de Hibernate 6 (Spring Boot 3) específicamente ante `JOIN FETCH` de colección + `Pageable`** en los métodos muertos de `CourtRepository` — se señala el patrón como conocido antipatrón en Hibernate 5, pero no se confirmó si Hibernate 6 lo maneja distinto, dado que el método no se ejecuta.

## Preguntas abiertas

- ¿Existen hoy filas duplicadas `(owner_id, club_id)` en `owner_club` en la base de producción? Determina si la migración de limpieza antes del `PRIMARY KEY` puede omitirse o no.
- ¿Cuál es el volumen actual/esperado de `court` y `reservation` por jugador? Determina la urgencia real de paginar `GetAllCourtsUseCase` y de agregar paginación/orden a `GetPlayerReservationsUseCase.execute()` (que hoy trae todo el historial de un jugador sin `ORDER BY` explícito ni límite — no se reporta como hallazgo aparte porque, a diferencia de `GetAllCourtsUseCase`, está acotado por jugador, no es un listado global, pero vale la pena tenerlo en el radar junto con la misma pregunta de volumen).

---

## Audit Validation

**Método de esta segunda pasada:** revisión independiente de solo lectura del informe de arriba, hecha por un segundo revisor. Para cada hallazgo se releyó directamente el archivo/línea citado (no se asumió correcta la cita), se buscaron los llamadores reales de cada método mencionado con `grep` sobre `src/main/java` completo, y se rastreó cada caso de uso hasta su controller/endpoint para confirmar accesibilidad real. No se modificó código, migraciones ni base de datos. No se ejecutó la aplicación ni ningún `EXPLAIN`.

### 1–3. Evidencia, severidad y separación problema-real/mejora-opcional

Los cinco hallazgos del informe tienen evidencia real y verificable — no encontré ninguna cita de archivo/línea que no correspondiera al código actual:

| Hallazgo | Evidencia releída | Verificación |
|---|---|---|
| `owner_club` sin PK/UNIQUE | `V1__baseline.sql:66-73` | ✅ Confirmado — tal cual se cita, comentario incluido |
| `Club.addOwner()` check-then-act | `Club.java:103-110` | ✅ Confirmado — `ownerIds.contains()` sobre `Set<Long>` en memoria, sin lock |
| `AddOwnerToClubUseCase` sin `findByIdForUpdate` | `AddOwnerToClubUseCase.java:28` | ✅ Confirmado — `clubRepository.findById(clubId)` plano |
| `GetAllCourtsUseCase` sin paginar | `GetAllCourtsUseCase.java:18-20` | ✅ Confirmado, y confirmé además el endpoint real: `CourtAdminController.getAll()` → `GET /api/courts/admin`, `@PreAuthorize("hasRole('ADMIN')")` — vivo y alcanzable |
| 5 métodos muertos en `CourtRepository` | líneas 21-23, 32, 34, 51 | ✅ Confirmado — `grep` de cada nombre contra todo `src/main/java` no devuelve ningún llamador fuera de la propia interfaz |
| `TicketEntity` sin `@UniqueConstraint` | `TicketEntity.java` | ✅ Confirmado — el `@Table` no tiene `uniqueConstraints`, a diferencia de `OwnerEntity`/`ClubEntity` |
| N+1 documentado en `ClubRepository` | líneas 53-54 | ✅ Confirmado — cité el comentario exacto, coincide carácter por carácter |
| Índice `branches` sin `club_id` | — | ⚠️ Ver sección 4 (Upgrade) — la premisa de esta observación es incorrecta |

La separación Confirmado/Riesgo potencial/Mejora opcional del informe original es, en general, honesta y bien calibrada — no encontré ningún caso de una mejora opcional presentada como si fuera un defecto, ni viceversa. Las severidades Medio para `owner_club` y `GetAllCourtsUseCase`, y Bajo/Opcional para el resto, son razonables **con una salvedad importante** (ver sección 3 abajo, hallazgo de `owner_club`).

### 4. Afirmaciones no demostrables con el código disponible

- **El campo "Impacto" de `owner_club` describe un escenario que no es alcanzable por ningún camino de código existente hoy** (desarrollo completo en la sección 6 más abajo). No es que sea "no verificable" — es que, rastreando el único llamador real de `Club.addOwner()`, se puede demostrar que ese escenario concreto no puede ocurrir con el código actual. Esto es más fuerte que una limitación de verificación: es una corrección concreta a una afirmación del informe.
- Todo lo demás marcado "no verificable" en el informe original (volumen real de datos, filas duplicadas existentes hoy, comportamiento de Hibernate 6 ante el antipatrón) sigue siendo correctamente no verificable desde el repositorio — no encontré manera de resolver ninguna de esas preguntas por lectura de código.

### 5. Contexto faltante antes de corregir cualquier cosa

- Antes de agregar `PRIMARY KEY (owner_id, club_id)` a `owner_club`: adicionalmente a la limpieza de duplicados ya señalada por el informe, hace falta decidir si el proyecto planea algún flujo futuro de "agregar un owner *existente* a un club" (hoy no existe — ver sección 6). Si nunca va a existir, la urgencia de esta migración baja considerablemente respecto a como está presentada.
- Antes de tocar `GetAllCourtsUseCase`/`GetAllOwnersUseCase`: falta el volumen real de `court`/`owner` (ya señalado, correcto).
- Antes de decidir sobre el índice de `branches` (ver sección 4): falta el volumen real de la tabla `branches` y la frecuencia de uso real del endpoint de búsqueda por nombre — sin eso no se puede afirmar que el índice actual (`idx_branches_club_verification_active`) sea insuficiente en la práctica, solo que estructuralmente no puede servir a esa query.
- Antes de tocar `owners.dni` (hallazgo nuevo, ver sección 7): falta confirmar si ya existen hoy en producción dos `owners` con el mismo DNI — igual que para `owner_club`, agregar un `UNIQUE` requeriría primero descartar/limpiar duplicados existentes.

### 6. Deep-dive `owner_club` — respuesta punto por punto

- **Definición real de la tabla:** confirmada — `V1__baseline.sql:66-73`. Dos `FOREIGN KEY` (`owner_id`→`owners.id`, `club_id`→`clubs.id`), sin `PRIMARY KEY` ni `UNIQUE` sobre el par. El comentario propio de la migración ("V2 agrega PRIMARY KEY... previa limpieza de duplicados") es una promesa que **nunca se cumplió**: la `V2` real (`V2__add_unique_reservation_slot.sql`) es sobre `reservation`, no sobre `owner_club`. Esto confirma exactamente el punto 9 del pedido (ver más abajo) — es evidencia real de *drift* entre lo que un comentario de migración anuncia y lo que el histórico de migraciones efectivamente hizo.
- **Constraints:** ninguna sobre `(owner_id, club_id)`. Las únicas constraints de la tabla son las dos FK. Confirmado directamente en el SQL, sin ambigüedad.
- **`Club.addOwner()`:** confirmado línea por línea (`Club.java:103-110`) — `if (ownerIds.contains(ownerId)) throw ...`, sobre un `Set<Long>` inmutable que ya fue cargado en memoria al leer el agregado. No hay `SELECT ... FOR UPDATE`, no hay lock optimista (no hay columna `@Version` en `ClubEntity`), no hay ningún mecanismo de sincronización.
- **¿Existe algún mecanismo de concurrencia?** Ninguno, en ningún nivel: ni lock pesimista (`AddOwnerToClubUseCase.execute()` usa `clubRepository.findById(clubId)` plano — confirmé que **no** existe ninguna variante `findByIdForUpdate` para `Club`, a diferencia de `Court`/`Branch`), ni lock optimista, ni `UNIQUE` de base. Es, tal como dice el informe, la única de las cuatro entidades con esta clase de invariante ("no duplicados") que no tiene ninguna de las tres capas de defensa.
- **¿Puede producirse realmente una duplicación bajo requests concurrentes?** — **Acá el informe original se equivoca en el mecanismo concreto que describe, aunque el hallazgo de fondo (constraint faltante) sigue siendo válido.** Rastreé con `grep` todos los llamadores de `Club.addOwner()` en `src/main/java`: hay **exactamente uno**, `AddOwnerToClubUseCase.execute()` (`AddOwnerToClubUseCase.java:35`). Ese método, antes de llamar a `addOwner(ownerId)`, siempre ejecuta primero `ownerGateway.register(command)` (línea 32), y `OwnerGatewayAdapter.register()` (`OwnerGatewayAdapter.java:24-46`) **siempre crea un `Owner` nuevo** vía `ownerRepository.save(owner).id()` — nunca devuelve el id de un owner ya existente. El id que llega a `addOwner()` es, por lo tanto, **siempre un id recién generado por `AUTO_INCREMENT`**, nunca reutilizado.
  - Consecuencia: para que dos transacciones concurrentes inserten la misma fila `(owner_id, club_id)`, harían falta dos llamadas que usen el **mismo** `owner_id` — y hoy no existe en el código ningún camino que agregue un owner *ya existente* a un club (no hay un "invitar owner existente" o "transferir club"). El escenario concreto que describe el informe ("dos altas concurrentes del mismo owner al mismo club... ambas insertan una fila en `owner_club`") **no es reproducible con el código actual**, porque no hay forma de que dos requests concurrentes compartan el mismo `owner_id` en esta ruta.
  - Esto **no invalida el hallazgo** — la ausencia de `PRIMARY KEY`/`UNIQUE` sigue siendo una brecha estructural real, y es exactamente el tipo de deuda que se paga cara el día que alguien agregue (razonablemente) un endpoint "agregar owner existente al club" reusando `Club.addOwner()` tal cual está, sin darse cuenta de que depende enteramente de un chequeo en memoria. Pero **la severidad de "explotable hoy" debe bajar**: no es un riesgo de concurrencia activo sobre el código actual, es una falta de defensa en profundidad sobre un invariante que, por ahora, ninguna ruta de escritura puede violar. Ver sección 3 (downgrade).
  - Nota aparte: si dos requests concurrentes llaman a `AddOwnerToClubUseCase.execute()` con el **mismo DNI/CUIL/email** (por ejemplo, doble click en un formulario de alta), ahí sí hay una condición de carrera real — pero es sobre la tabla `owners`/`users`, no sobre `owner_club`, y con un matiz importante: ver hallazgo nuevo en la sección 7.
- **Test de esta carrera:** confirmado — `grep` de `owner_club` y `OwnerAlreadyExists` sobre todo `src/test/java` no devuelve resultados. No hay ningún test, ni siquiera uno que ejercite el chequeo en memoria de `Club.addOwner()` de forma aislada.

### 7. `GetAllCourtsUseCase` — ¿debería paginarse según el patrón del resto?

Sí, la comparación del informe es correcta y la reverifiqué directamente:

- `GetAllClubsUseCase.execute(PageRequest)` → `clubRepository.findAll(pageRequest)` → `PageResult<Club>` (pagina).
- `GetAllBranchesUseCase` está acotado por `clubId` (nunca es un listado global — no es comparable).
- `GetAllCourtsUseCase.execute()` → `courtRepository.findAll()` → `List<Court>` (**no** pagina), confirmado en `CourtRepositoryAdapter.java:100-101` (`courtRepository.findAll()` sobre el `JpaRepository` sin `Pageable`).
- El endpoint es real y alcanzable: `CourtAdminController.getAll()`, `GET /api/courts/admin`, `@PreAuthorize("hasRole('ADMIN')")`.

Es, tal como dice el informe, una inconsistencia real de patrón dentro del propio código (no una preferencia externa impuesta por la revisión) — el propio proyecto ya resolvió este problema para `Club` y no lo replicó para `Court`. Confirmado sin objeciones.

### 8. Métodos muertos de `CourtRepository` — ¿realmente sin referencias?

Confirmado con `grep` de cada nombre de método contra `src/main/java` completo (no solo el archivo del repositorio):

- `findByNameAndBranchIdAndSportId` — 0 llamadores fuera de la declaración.
- `findByIdAndBranch_Id` — 0 llamadores.
- `findByBranch_VerificationStatusAndBranch_ActiveStatus` (`List`, línea 23) — 0 llamadores.
- `findByBranch_VerificationStatusAndBranch_ActiveStatus` (`Page`, línea 51) — 0 llamadores.
- `findByCourtWithSchedule` — 0 llamadores.
- `findBySport_Id` — 0 llamadores. Confirmé además, releyendo `DeleteSportUseCase.java`, que este caso de uso no tiene ningún guard equivalente a `hasReservations()`/`hasBranches()` (patrón que sí existe para `Branch`, `Court` y `Club` — confirmé los tres con `grep` de `hasReservations|hasBranches`). La única protección real hoy es la FK `fk_court_sport` sin `ON DELETE CASCADE` (rechazo a nivel de base con `DataIntegrityViolationException` cruda, no una excepción de dominio).

Los cinco están, efectivamente, sin ningún llamador real. Confirmado sin objeciones — es código muerto tal como lo describe el informe.

### 9. Diferencias entre comentarios de migraciones y lo que realmente ejecutan

Releí las tres migraciones completas contra lo que sus comentarios afirman:

- **`V1__baseline.sql`: discrepancia real y ya es, en sí misma, la mejor evidencia del hallazgo de `owner_club`.** El comentario de la línea 70 dice *"V2 agrega PRIMARY KEY (owner_id, club_id) previa limpieza de duplicados"* — pero la `V2` que realmente existe en el repo (`V2__add_unique_reservation_slot.sql`) no toca `owner_club` en absoluto; es sobre `reservation`. El comentario documenta una intención que quedó incumplida — esto no es un error del informe, es un hecho real del repositorio que el informe correctamente señala.
- **`V2__add_unique_reservation_slot.sql`:** el comentario describe con precisión lo que el SQL ejecuta (columna generada `active_slot_court_id` + `UNIQUE KEY` sobre `(active_slot_court_id, reservation_day, start_time)`) — sin discrepancia. La afirmación "0 filas en reservation, sin conflictos posibles" no es verificable desde el repositorio (es una afirmación sobre el estado de una base real en el momento de escribir la migración), pero no contradice nada del código actual.
- **`V3__add_branch_cancellation_window.sql`:** el comentario describe exactamente la secuencia de tres pasos que el SQL ejecuta (columna `NULL` → backfill a 12 → `NOT NULL`) — sin discrepancia.

Conclusión: solo `V1` tiene una discrepancia real entre lo que su comentario anuncia y lo que el historial de migraciones efectivamente hizo, y es precisamente la que ya sostiene el hallazgo principal del informe. No encontré discrepancias adicionales no reportadas en `V2`/`V3`.

### 10. Garantías de integridad que dependen solo de código de aplicación

El informe cubre correctamente `owner_club`. Verificando el resto de las tablas por la misma vía (comparar cada invariante de unicidad relevante contra un `UNIQUE`/`PRIMARY KEY` real), encontré **una guardia adicional del mismo tipo que el informe original no reportó**:

- **`owners.dni` no tiene ningún `UNIQUE` a nivel de base** (`V1__baseline.sql:21-29` — `owners` solo declara `UNIQUE KEY uq_owners_cuil (cuil)`; `dni` es `BIGINT NOT NULL` sin más). La unicidad de DNI depende exclusivamente de `OwnerRepository.existsByDni(dni)`, un chequeo puro en memoria/consulta-previa sin ningún lock, invocado de forma independiente (duplicando la misma lógica) en dos lugares: `RegisterOwnerUseCase.java:30` (alta de owner standalone, `POST /api/owners`, solo ADMIN) y `OwnerGatewayAdapter.register()` (vía `AddOwnerToClubUseCase`, ADMIN o dueño del club). Es el mismo patrón check-then-act que `Club.addOwner()`, pero a diferencia de ese caso, **acá sí hay una ruta de escritura reachable y ejercitable hoy** con datos idénticos: dos POST casi simultáneos con el mismo DNI (p. ej. doble submit desde un formulario admin, o un reintento de red tras timeout) pueden ambos pasar `existsByDni` antes de que el otro haga commit, y ambos insertan — sin `UNIQUE`, ninguno falla. Por contraste, `cuil` y `email` (ambos chequeados en la misma secuencia, misma clase de invariante) sí tienen respaldo real de base (`uq_owners_cuil`, `uq_users_email`), así que el patrón correcto ya existe dos veces en la misma clase para las otras dos columnas y no se aplicó a `dni`.
  - **Confianza:** Confirmado (ausencia de constraint en el schema y del lock en el código); alcance de explotación acotado a usuarios autenticados con rol ADMIN u OWNER-dueño-del-club (no es una superficie pública/anónima).
  - Este es el hallazgo que la metodología de la propia Skill pide capturar en el paso "verificar UNIQUE para cada invariante importante, separado de si la aplicación también lo chequea" — el informe original lo aplicó a `owner_club` pero no lo extendió a `owners.dni` pese a estar en el mismo archivo de migración y el mismo flujo de alta.

No encontré otras garantías de integridad relevantes que dependan solo de código de aplicación, más allá de las dos ya mencionadas (`owner_club`, `owners.dni`) y de lo que ya cubre el otro informe (`docs/software-review-2026-08-26.md`) para dominios fuera del alcance de este documento (p. ej. el lock de reservas, que sí está respaldado por partida doble).

---

### Resumen de esta validación

**Confirmed findings** (evidencia y severidad sostenidas sin cambios):
1. `GetAllCourtsUseCase` sin paginar — Medio, confirmado, endpoint verificado en vivo.
2. 5 métodos muertos en `CourtRepository` — Bajo, confirmado, cero llamadores verificado exhaustivamente.
3. `TicketEntity` sin `@UniqueConstraint` documentada — Opcional, confirmado.
4. N+1 documentado y deliberado en `ClubRepository` — Opcional, confirmado, cita textual exacta.
5. Ausencia de `PRIMARY KEY`/`UNIQUE` en `owner_club` (el defecto de schema en sí) — Confirmado.
6. `Club.addOwner()` sin ningún mecanismo de lock/sincronización — Confirmado.

**Findings requiring more evidence** (sin cambios respecto al informe original — siguen siendo correctamente no verificables):
- Filas duplicadas hoy en `owner_club` en la base real.
- Volumen real de `court`, `owner`, `branches` y su efecto en la urgencia de paginar/indexar.
- Comportamiento real de Hibernate 6 ante `JOIN FETCH` de colección + `Pageable` en los métodos muertos.

**Findings that should be downgraded:**
- **El "Impacto" de `owner_club`, tal como está redactado, describe un escenario no reproducible con el código actual** (ver sección 6). El hallazgo de fondo (constraint de base faltante, sin defensa en profundidad) se sostiene y sigue siendo Medio como recomendación preventiva, pero la narrativa de "dos altas concurrentes de HOY producen duplicados" debería reescribirse como "no explotable por ningún camino de escritura existente hoy, pero queda sin protección ante el primer camino nuevo que reutilice un `owner_id` existente" — es una diferencia relevante para priorización: esto no compite en urgencia con el hallazgo Alto de rate limiting del otro informe.

**Findings that should be upgraded:**
- **"Sin índice para filtrar `branches` por `(verification_status, active_status)` sin `club_id`"** — el informe original afirma que "hoy no lo necesita ninguna query viva" y lo clasifica como Mejora opcional. Es incorrecto: `BranchRepository.findByNameContainingIgnoreCaseAndVerificationStatusAndActiveStatus` (sin `@Query`, derivado por nombre) filtra exactamente por `verification_status = APPROVED AND active_status = ACTIVE` sin `club_id`, y es alcanzable en producción vía `BranchController` → `SearchBranchesByNameUseCase` → `BranchRepositoryPort.searchApprovedByName()`. El índice existente `idx_branches_club_verification_active(club_id, verification_status, active_status)` no puede servir a esta query en absoluto (la regla de prefijo izquierdo lo excluye por completo al no filtrar por `club_id`), así que hoy corre como table scan completo de `branches` filtrado en memoria/motor por dos igualdades más un `LIKE` no sargable. Recomiendo subirlo de "Mejora opcional, no aplica hoy" a 🔵 **Low (Confirmado)** — es una query real, no hipotética; no lo subo a Medio porque el volumen de `branches` sigue sin verificarse y es plausible que sea bajo en esta etapa.

**Missing checks** (no estaban en el informe original):
- **`owners.dni` sin `UNIQUE` a nivel de base**, con `existsByDni` como único chequeo (check-then-act, duplicado en dos flujos independientes) — ver sección 10. Recomiendo agregarlo como hallazgo propio, 🔵 **Low-Medio (Confirmado)**: mismo mecanismo que `owner_club` pero con una ruta de escritura genuinamente alcanzable hoy (a diferencia de `owner_club`), aunque acotada a usuarios ADMIN/OWNER autenticados, no a tráfico anónimo.
- El informe no verificó, para `owner_club`, si `Club.addOwner()` tiene un único llamador o varios — ese chequeo (parte del método de la propia Skill, "trazar cada método de query hasta un llamador real en código alcanzable") habría revelado directamente la corrección de la sección anterior.
- No se comparó `owners` contra el mismo criterio de UNIQUE que sí se aplicó a `owner_club`, pese a estar en el mismo archivo de migración — una pasada de "todas las columnas con semántica de identidad real (DNI, CUIT, email, legal_name) tienen su UNIQUE" habría detectado el hueco de `dni` sin necesidad de leer casos de uso.

No se modificó ningún archivo de código, migración ni configuración como parte de esta validación.
