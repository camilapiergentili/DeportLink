# 🏟️ DeportLink – Plataforma de Reservas Deportivas

API REST backend para gestionar clubes, sucursales, canchas, agendas y reservas deportivas, con verificación documental de establecimientos y control de acceso por roles.

> **Stack real (ver [pom.xml](pom.xml)):** Java 21 · Spring Boot 3.5.5 · Spring Security · JWT · Spring Data JPA / Hibernate · MySQL · Flyway · MapStruct

> ⚠️ Este README describe el estado del branch **`development`**, que es la fuente de verdad actual del proyecto. Ver la sección [Estado del branch](#-estado-del-branch) al final.

---

## ✨ ¿Qué resuelve DeportLink?

DeportLink conecta jugadores con clubes deportivos: los dueños de clubes administran sus instalaciones y disponibilidad, y los jugadores buscan y reservan canchas.

- **Admin** → da de alta cuentas de Owner (`POST /api/owners`, requiere rol ADMIN — no es autoregistro) y aprueba/rechaza clubes y sucursales antes de que salgan al público
- **Owner** → una vez creada su cuenta, administra sus clubes, sucursales, canchas y agendas de horarios
- **Player** → se autoregistra libremente (`POST /api/players` es el único endpoint de escritura público además del login), busca canchas disponibles y reserva, cancela o reprograma turnos

### Estados del sistema

Tres enums de estado independientes gobiernan el ciclo de vida de las entidades (valores reales, ver `enums/`):

| Enum | Valores | Gobierna |
|---|---|---|
| `VerificationStatus` | `PENDING` · `APPROVED` · `REJECTED` | Aprobación de Club/Branch por un Admin |
| `ActiveStatus` | `ACTIVE` · `INACTIVE` | Encendido/apagado de Club/Branch/Court, decisión del Owner |
| `StatusReservation` | `RESERVADO` · `CANCELADO` · `REPROGRAMADO` · `FINALIZADO` | Ciclo de vida de una reserva |

### Jerarquías

```
User (base)
├── Owner  →  administra clubes y sucursales (cuenta creada por un Admin)
└── Player →  reserva canchas (autoregistro)

Club
└── Branch (Sucursal)
    └── Court (Cancha)
        ├── Schedule (Agenda de horarios)
        └── Reservation (Reserva)
```

---

## 🧱 Arquitectura: hexagonal (Ports & Adapters) + Use Cases

El dominio no depende de Spring, JPA ni de ningún framework — las dependencias apuntan hacia adentro. Cada capa real del proyecto:

| Capa | Vive en | Qué hay ahí |
|---|---|---|
| **Dominio** | `domain/model/` | Records inmutables (`Reservation`, `Club`, `Branch`, `Court`...) con las invariantes de negocio encapsuladas en el propio agregado — no en un service. Ver más abajo. |
| **Puertos de salida (dominio)** | `domain/port/out/` | Interfaces que el dominio necesita para persistir/consultar (`ReservationRepositoryPort`, `CourtRepositoryPort`, etc.), sin saber que del otro lado hay JPA. |
| **Puertos de salida (aplicación)** | `application/port/out/` | Una segunda familia de puertos más liviana — *gateways* de solo lectura (`CourtGateway`, `PlayerGateway`, `ScheduleGateway`, `OwnerGateway`) que los use cases usan cuando solo necesitan un snapshot de datos, no el agregado completo. |
| **Casos de uso** | `application/usecase/{branch,club,court,owner,player,reservation,schedule,sport}/` | Una clase = una operación de negocio (`BookReservationUseCase`, `DeleteBranchUseCase`...). Orquestan puertos y dominio; no tienen lógica de negocio propia más allá de la orquestación. |
| **Adapters de infraestructura** | `infrastructure/adapter/` | Implementan los puertos de dominio/aplicación contra JPA — mapeo manual dominio ↔ entidad (`toDomain()` / entidad de vuelta). |
| **Entidades JPA** | `model/entity/` | `@Entity` puras, sin lógica de negocio — solo mapeo objeto-relacional. |
| **Repositorios Spring Data** | `persistence/repository/` | Interfaces `JpaRepository`/`CrudRepository`, incluyendo las queries `@Lock(PESSIMISTIC_WRITE)` que sostienen la garantía de concurrencia (ver más abajo). |
| **Controllers** | `controller/` | HTTP puro: mapeo DTO↔dominio (vía `mapper/dto/`), `@PreAuthorize`, delegación al use case correspondiente. |

**Dominio inmutable por diseño.** Por ejemplo, `Reservation` es un `record`; sus transiciones de estado (`cancel()`, `markAsRescheduled()`) validan sus propias reglas (ventana de cancelación, transición de estado válida) y devuelven una **instancia nueva** en vez de mutar — nunca hay un estado intermedio corrupto observable desde afuera.

**Errores de negocio:** toda excepción de dominio extiende `BusinessException` y declara su propio `HttpStatus` en el constructor. `GlobalExceptionHandler` tiene un único `@ExceptionHandler(BusinessException.class)` — agregar una excepción nueva no requiere tocar el handler (abierto/cerrado), a diferencia del enfoque anterior de listas `@ExceptionHandler({...})` a mano.

---

## 🛠️ Stack técnico

Versiones reales, tomadas de `pom.xml`:

| Tecnología | Versión |
|---|---|
| Java | **21** |
| Spring Boot | **3.5.5** (`spring-boot-starter-parent`) |
| Spring Security + JWT | `spring-boot-starter-security` + `jjwt` 0.11.5 |
| Spring Data JPA / Hibernate | vía `spring-boot-starter-data-jpa` |
| MySQL | `mysql-connector-j` (runtime) |
| Flyway | `flyway-core` + `flyway-mysql` |
| MapStruct | 1.5.5.Final |
| Lombok | 1.18.32 |
| Testing | JUnit 5, Mockito 5.7, Spring Security Test, H2 (unit), **Testcontainers** (MySQL real) |
| Cobertura | JaCoCo 0.8.11 |

---

## 🔐 Seguridad

**Autenticación:** JWT stateless (`JwtFilter` + `JwtUtil`). Token con expiración de 10hs (`jwt.expiration=36000000` ms). Solo dos endpoints son públicos — `POST /api/auth/login` y `POST /api/players` — todo lo demás requiere `Authentication` válida (`SecurityConfig`: `.anyRequest().authenticated()`).

**Autorización por método:** casi todos los endpoints de escritura llevan `@PreAuthorize`. Además de roles (`hasRole('ADMIN')`, etc.), hay tres beans de autorización que verifican **ownership contra la base**, no contra el token:

```java
@courtAuthorization.isOwnerOfCourt(#idCourt, authentication)
@branchAuthorization.isOwnerOfBranch(#idBranch, authentication)
@clubAuthorization.isOwnerOfClub(#idClub, authentication)
```

Cada uno hace una query directa (`existsByCourtAndOwner`, `existsByIdAndClub_Owners_Id`, `existsByIdAndOwners_Id`) — un Owner no puede operar sobre canchas/sucursales/clubes que no son suyos aunque adivine el id.

**Protección IDOR en reservas:** `CancelReservationUseCase` y `RescheduleReservationUseCase` no devuelven 403 cuando un Player intenta operar sobre la reserva de otro — devuelven **404** (`ReservationNotFoundException`), igual que si el id no existiera. Del código:

> *"belongsTo is a domain query — the aggregate knows who owns it. We throw ReservationNotFound (not Unauthorized) to avoid leaking reservation existence to other players."*

Un 403 confirmaría que el id existe y es de otra persona; el 404 no revela nada.

**Rate limiting de login:** `LoginAttemptService` bloquea una IP 15 minutos después de 5 intentos fallidos (`TooManyRequestsException`, 429). Es en memoria del proceso — funciona correctamente con el despliegue actual (Railway, 1 sola instancia). Ver [Limitaciones conocidas](#-limitaciones-conocidas--trabajo-futuro).

---

## 📌 Endpoints principales

Todos los paths tienen prefijo `/api`. `Autenticado` = cualquier rol con JWT válido; los demás roles son adicionales a eso.

### Autenticación y registro
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/auth/login` | Login, devuelve el JWT | Público |
| `POST` | `/players` | Autoregistro de un Player | Público |
| `POST` | `/owners` | Alta de un Owner | Admin |

### Clubes
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/clubs/owner` | Crear club | Owner |
| `PUT` / `DELETE` | `/clubs/owner/{idClub}` | Actualizar / eliminar | Dueño del club |
| `POST` / `DELETE` | `/clubs/owner/{idClub}/owners[/{idOwner}]` | Agregar / quitar co-owner | Dueño del club |
| `PATCH` | `/clubs/owner/{idClub}/activate` \| `/deactivate` | Encender / apagar | Dueño del club |
| `PATCH` | `/clubs/admin/{idClub}/approve` \| `/reject` | Aprobar / rechazar | Admin |
| `GET` | `/clubs/{id}`, `/clubs/approved`, `/clubs/search` | Consultas (paginadas) | Autenticado |
| `GET` | `/clubs/all` | Listar todos, sin filtrar por estado | Admin |

### Sucursales (Branch)
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/branches/owner` | Crear sucursal | Dueño del club |
| `PUT` / `DELETE` | `/branches/owner/{id}` | Actualizar / eliminar | Dueño |
| `PATCH` | `/branches/owner/{idBranch}/activate` \| `/desactivate` | Encender / apagar | Dueño |
| `PATCH` | `/branches/admin/{id}/approve` \| `/reject` | Aprobar / rechazar | Admin |
| `GET` | `/branches/{idBranch}/approved`, `/branches/{idClub}/active-approved`, `/branches/search`, `/branches/by-sport/{sportId}`, `/branches/nearby` | Búsquedas públicas para Players | Autenticado |
| `GET` | `/branches/admin/{id}`, `/branches/admin/{idClub}/club` | Consulta administrativa | Admin |

### Canchas (Court)
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/courts/owner` | Crear cancha | Dueño de la sucursal |
| `PUT` / `DELETE` | `/courts/owner/{idCourt}` | Actualizar / eliminar | Dueño |
| `PATCH` | `/courts/owner/{idCourt}/branch` | Mover a otra sucursal | Dueño |
| `PATCH` | `/courts/owner/{idCourt}/price` | Cambiar precio | Dueño |
| `PATCH` | `/courts/owner/{idCourt}/activate` \| `/deactivate` | Encender / apagar | Dueño |
| `GET` | `/courts/{idCourt}/active`, `/courts/active[/paginated]`, `/courts/branch/{idBranch}/active[/paginated]`, `/courts/branch/{idBranch}/sport/{idSport}` | Búsqueda de canchas disponibles | Autenticado |
| `GET` | `/courts/admin`, `/courts/admin/branch/{idBranch}` | Listado administrativo | Admin |

### Agenda (Schedule)
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/schedules/court/{idCourt}` | Agregar horario | Dueño de la cancha |
| `PUT` / `DELETE` | `/schedules/{idSchedule}/court/{idCourt}` | Actualizar / eliminar | Dueño |
| `GET` | `/schedules/court/{idCourt}`, `/schedules/court/{idCourt}/day` | Consultar agenda | Autenticado |

### Reservas
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/reservations` | Reservar turno | Player |
| `DELETE` | `/reservations/{reservationId}` | Cancelar | Player dueño de la reserva |
| `PUT` | `/reservations/{reservationId}/reschedule` | Reprogramar | Player dueño de la reserva |
| `GET` | `/reservations/available` | Horarios libres de una cancha/día | Autenticado |
| `GET` | `/reservations/player/{playerId}` | Historial de reservas | Admin o el propio Player |

### Deportes (Sport)
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` / `DELETE` | `/sports` \| `/sports/{idSport}` | Alta / baja | Admin |
| `GET` | `/sports`, `/sports/{idSport}` | Consultar | Autenticado |

---

## 🔒 Concurrencia: evitar la doble reserva

La garantía central del sistema: **N requests simultáneas para el mismo `court + day + startTime` nunca resultan en más de una reserva `RESERVADO`.**

**Estrategia de aplicación:** `BookReservationUseCase` y `RescheduleReservationUseCase` toman un **lock pesimista** (`SELECT ... FOR UPDATE`, vía `findByIdForUpdate`) sobre la fila de `Court` como **primera** operación de la transacción — antes de leer los slots ocupados. Esto serializa a nivel de fila cualquier request concurrente para esa cancha: la segunda transacción queda bloqueada hasta que la primera confirma o revierte, así que nunca dos requests pasan la validación de disponibilidad "al mismo tiempo".

**Verificado contra MySQL real, no mocks.** H2 no reproduce fielmente el locking de InnoDB (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`), así que la garantía se prueba con **Testcontainers** (contenedor MySQL descartable por test) lanzando hilos concurrentes de verdad:

- `BookReservationConcurrencyTest` — 10 reservas simultáneas al mismo slot, gana exactamente una
- `RescheduleReservationConcurrencyTest` — 2 reprogramaciones simultáneas al mismo turno nuevo
- `BookReservationVsDeleteCourtConcurrencyTest` — reservar vs. borrar la cancha en paralelo
- `BookReservationVsDeleteBranchConcurrencyTest` — reservar vs. borrar la sucursal (lock múltiple sobre todas sus canchas) en paralelo

**Defensa en profundidad a nivel de base:** además del lock de aplicación, la migración `V2__add_unique_reservation_slot.sql` agrega una columna generada `active_slot_court_id` (NULL salvo que `status = 'RESERVADO'`) con un `UNIQUE KEY (active_slot_court_id, reservation_day, start_time)`. MySQL no tiene índices únicos parciales nativos (a diferencia de Postgres); este es el workaround estándar — y actúa como red de seguridad aunque algún camino de código futuro se salte el lock de aplicación. Probado en `ReservationUniqueSlotConstraintTest`.

---

## 🚀 Cómo correr el proyecto localmente

> ⚠️ El repo **no tiene `Dockerfile`**, y `spring.docker.compose.enabled=false` en `application.properties` desactiva el auto-arranque de `compose.yaml` por parte de Spring Boot. El flujo "`docker-compose up --build`" de versiones anteriores de este README **no funciona tal cual** — el compose.yaml de la raíz sirve solo como definición de referencia para MySQL, hay que levantarlo (y mapear el puerto) a mano si se quiere usar.

**1. Base de datos MySQL** — cualquiera de las dos:
- Instancia propia local, o
- `docker run -d -p 3306:3306 -e MYSQL_DATABASE=deportlink -e MYSQL_ROOT_PASSWORD=<algo> mysql:8` (o ajustar `compose.yaml` agregando el mapeo de puerto y correrlo con `docker compose up -d`)

**2. Variables de entorno** (`application.properties` las lee todas por `${...}`, sin defaults hardcodeados salvo `cors.allowed-origins`):

```env
DB_URL=jdbc:mysql://localhost:3306/deportlink?useSSL=false&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=tu_contraseña
JWT_SECRET=una_clave_larga_y_secreta
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Si `CORS_ALLOWED_ORIGINS` no está seteada (y no corrés con el perfil `dev`), la app **no arranca**: `SecurityConfig` valida esto explícitamente al levantar el contexto, en vez de subir con CORS mal configurado y que el problema aparezca recién con el frontend fallando en producción.

**3. Compilar y ejecutar**
```bash
./mvnw clean install
./mvnw spring-boot:run
```
API disponible en `http://localhost:8080`.

### Perfil default vs. perfil `dev`

| | Perfil default (sin flags) | Perfil `dev` |
|---|---|---|
| `ddl-auto` | `validate` — Hibernate solo compara el esquema contra las entidades y falla si no coinciden, nunca lo modifica | `update` — Hibernate puede alterar el esquema automáticamente |
| Quién administra el esquema | **Flyway**, exclusivamente | Hibernate (para iterar rápido sin escribir una migración por cada cambio chico) |
| `cors.allowed-origins` | Viene de `CORS_ALLOWED_ORIGINS` (obligatoria) | Fija en `http://localhost:5173`, no depende de ninguna variable |
| Cuándo usarlo | Staging / producción — **siempre** | Solo desarrollo local, opt-in explícito |

Activar el perfil `dev`:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# o
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

> ⚠️ **`ddl-auto=update` nunca debe usarse fuera de desarrollo local.** Fuera de tu máquina el esquema lo controla exclusivamente Flyway — usar `update` en un ambiente compartido puede alterar el esquema real de forma silenciosa y dejarlo inconsistente con las migraciones versionadas.

---

## 🗃️ Migraciones de base de datos

Flyway administra el esquema desde `src/main/resources/db/migration/`. Migraciones actuales:

| Migración | Qué hace |
|---|---|
| `V1__baseline.sql` | Baseline sobre el esquema ya creado por Hibernate antes de introducir Flyway (`spring.flyway.baseline-on-migrate=true`) |
| `V2__add_unique_reservation_slot.sql` | Columna generada + `UNIQUE KEY` para la garantía anti-doble-reserva a nivel de base (ver [Concurrencia](#-concurrencia-evitar-la-doble-reserva)) |
| `V3__add_branch_cancellation_window.sql` | Ventana de cancelación configurable por sucursal |

**Convención:** todo cambio de esquema entra por una migración versionada nueva, nunca por `ddl-auto=update` fuera del perfil `dev` en desarrollo local.

---

## 🧪 Testing

Cuatro niveles, cada uno con un motivo concreto para existir (`src/test/java/com/deportlink/deportlink/`):

| Nivel | Dónde | Con qué | Por qué |
|---|---|---|---|
| **Dominio puro** | `domain/` | JUnit 5, sin Spring | Las invariantes (`Reservation.cancel()`, transiciones de estado) se prueban como lo que son: lógica Java pura, sin levantar contexto |
| **Casos de uso** | `usecase/{branch,club,court,...}` | Mockito | Verifica orquestación (qué puerto se llama, en qué orden) mockeando los puertos — rápido, sin base de datos |
| **Controllers** | `controller/` | `@WebMvcTest` + MockMvc | Verifica serialización, `@PreAuthorize`, manejo de errores HTTP, con `@Import` de los mappers/config de seguridad reales |
| **Integración de concurrencia** | `usecase/reservation/*ConcurrencyTest`, `persistence/ReservationUniqueSlotConstraintTest` | **Testcontainers (MySQL 8 real)** | Los tests de Mockito de `BookReservationUseCaseTest` solo verifican *orden* de invocación, no que el lock realmente serialice transacciones concurrentes. H2 no reproduce fielmente `PESSIMISTIC_WRITE`/`SELECT...FOR UPDATE` de InnoDB — probar la garantía de "no doble reserva" en serio requiere un motor real con hilos reales compitiendo |

Correr todo: `./mvnw clean test` (requiere Docker corriendo, por los tests de Testcontainers). Estado actual: **239 tests, 0 failures, 0 errors**.

---

## 🚧 Limitaciones conocidas / trabajo futuro

- **Rate limiting de login en memoria, no distribuido**: `LoginAttemptService` cuenta intentos fallidos en un `ConcurrentHashMap` local al proceso. Correcto para el despliegue actual (Railway, 1 sola instancia, sin réplicas). Si en el futuro se activan réplicas, cada instancia lleva su propio conteo por separado y el rate limit deja de ser efectivo — antes de escalar a más de una instancia, migrar a un store compartido (Redis o una tabla). Ver auditoría del 20/08/2026.

- **Sin refresh token ni revocación de JWT**: `AuthController` solo expone `/login`. El token vive hasta su expiración natural (10hs) sin forma de invalidarlo del lado del servidor — no hay logout real ni manera de revocar un token comprometido antes de que expire.

- **Modelo de `Address` compartido entre dos usos distintos**: una única `AddressEntity`/tabla `address` sirve tanto a `Branch` (relación 1:1 vía `address_id`, sin noción de "default") como a `Player` (`Set<AddressEntity>`, con flag `isDefault` que solo tiene sentido ahí). Son dos formas de uso genuinamente distintas modeladas sobre la misma entidad — candidato a separar en dos modelos cuando se retome.

- **Mapeo manual repetido en `infrastructure/adapter/`**: `OwnerRepositoryAdapter`, `PlayerRepositoryAdapter`, `SportRepositoryAdapter` y `ScheduleRepositoryAdapter` repiten el mismo patrón de mapeo dominio↔entidad campo a campo. Decisión tomada: no migrar a MapStruct todavía — el costo de la abstracción no se justifica para 4 clases chicas sin lógica adicional. Revisar si la carpeta crece o el mapeo se complica.

- **Limpieza menor — naming inconsistente `desactivate` vs `deactivate`**: `BranchOwnerController` expone `PATCH /branches/owner/{idBranch}/desactivate` (con "s"), mientras que el mismo verbo en `CourtOwnerController` y `ClubOwnerController` es `.../deactivate`. Es solo una inconsistencia de naming en la ruta (no afecta funcionalidad ni seguridad) — cambiarla es un breaking change de API menor para cualquier cliente ya integrado contra `/desactivate`, así que conviene planearlo como una deprecación (aceptar ambas rutas por un tiempo) en vez de un rename directo.

---

## 🌿 Estado del branch

`main` está desactualizado respecto a `development`. Diverge en `ff2a62d` ("introduce Flyway with baseline"); a `development` le siguen **117 archivos** (4371 inserciones, 313 eliminaciones sobre `main`) que `main` no tiene, incluyendo:

- Las correcciones de seguridad de esta ronda (IDOR en reservas, `@PreAuthorize` granular por ownership)
- Los locks pesimistas y la constraint única que sostienen la garantía de no-doble-reserva (ver [Concurrencia](#-concurrencia-evitar-la-doble-reserva))
- Toda la suite de tests de concurrencia con Testcontainers

**`development` es la fuente de verdad actual del proyecto** — este README describe ese branch, no `main`.

---

## 👩‍💻 Autora

**Camila Piergentili**
Técnica Universitaria en Programación · Profesora de Matemática
