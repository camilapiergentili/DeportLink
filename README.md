# 🏟️ DeportLink – Plataforma de Reservas Deportivas

API REST backend para gestionar clubes, sucursales, canchas y reservas deportivas. Incluye sistema de verificación documental, activación de establecimientos y control de acceso por roles.

> **Stack:** Java 17 · Spring Boot · Spring Security · JWT · Spring Data JPA · Hibernate · MySQL · Docker

---

## ✨ ¿Qué resuelve DeportLink?

DeportLink conecta jugadores con clubes deportivos, permitiendo que dueños de clubes administren sus instalaciones y que jugadores encuentren y reserven canchas disponibles de forma simple.

- **Owners (dueños)** → registran clubes, sucursales y canchas, cargan documentación y gestionan disponibilidad
- **Players (jugadores)** → buscan canchas disponibles, hacen reservas y las cancelan
- **Admin global** → verifica y aprueba establecimientos antes de que salgan al público
- **Sistema de estados** → cada entidad tiene su propio ciclo de vida (pendiente → aprobado → activo)

---

## 🧩 Modelo de Dominio

### Jerarquía de usuarios

```
User (base)
├── Owner  →  administra clubes y sucursales
└── Player →  realiza y consulta reservas
```

### Jerarquía de establecimientos

```
Club
└── Branch (Sucursal)
    └── Court (Cancha)
        ├── Schedule (Agenda de horarios)
        └── Reservation (Reserva)
```

### Estados del sistema

Cada entidad tiene dos dimensiones de estado independientes:

| Estado | Valores | Controlado por |
|---|---|---|
| `verificationStatus` | PENDING · APPROVED · REJECTED | Admin global |
| `activeStatus` | ACTIVE · DESACTIVE | Owner del club |
| `statusReservation` | PENDING · CONFIRMED · CANCELLED | Sistema / Player |

---

## 🔐 Seguridad: JWT + Roles

Autenticación stateless con JSON Web Tokens. El rol del usuario determina qué endpoints puede consumir.

### Flujo de autenticación

```
POST /auth/login
→ Body: { "email": "...", "password": "..." }
← Response: { "token": "eyJhbGci..." }
```

Incluir en cada request:
```
Authorization: Bearer <token>
```

### Permisos por rol

| Rol | Capacidades |
|---|---|
| `ADMIN` | Verificar/rechazar clubes y sucursales, gestión global |
| `OWNER` | Crear y administrar sus clubes, sucursales y canchas |
| `PLAYER` | Buscar canchas disponibles, crear y cancelar reservas |

---

## 🛠️ Tecnologías

| Tecnología | Uso |
|---|---|
| Java 17 | Lenguaje principal |
| Spring Boot | Framework base |
| Spring Security | Seguridad y autenticación |
| JWT | Tokens de sesión stateless |
| Spring Data JPA + Hibernate | Persistencia ORM |
| MySQL | Base de datos relacional |
| Maven | Gestión de dependencias |
| Docker / Docker Compose | Contenedores y orquestación |

---

## 🚀 Cómo ejecutar el proyecto

### Opción 1: Con Docker (recomendado)

```bash
git clone https://github.com/camilapiergentili/DeportLink.git
cd DeportLink
git checkout development
docker-compose up --build
```

### Opción 2: Local con Maven

**1. Clonar el repositorio**
```bash
git clone https://github.com/camilapiergentili/DeportLink.git
cd DeportLink
git checkout development
```

**2. Crear el archivo `.env` en la raíz**
```env
DB_URL=jdbc:mysql://localhost:3306/deportlink?useSSL=false&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=tu_contraseña
JWT_SECRET=mi_clave_super_secreta
SERVER_PORT=8080
```

**3. Crear la base de datos**
```sql
CREATE DATABASE deportlink;
```

**4. Compilar y ejecutar**
```bash
./mvnw clean install
./mvnw spring-boot:run
```

La API quedará disponible en: `http://localhost:8080`

---

## 📁 Estructura del proyecto

```
DeportLink/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/deportlink/
│       │       ├── auth/         # JWT y seguridad
│       │       ├── controllers/  # Endpoints REST
│       │       ├── models/       # Entidades JPA (User, Club, Branch, Court...)
│       │       ├── repositories/ # Acceso a datos
│       │       └── services/     # Lógica de negocio
│       └── resources/
│           └── application.properties
├── compose.yaml
├── pom.xml
└── .env
```

---

## 📌 Endpoints principales

### Autenticación
| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/auth/login` | Login y obtención de token | Público |
| `POST` | `/auth/register` | Registro de usuario | Público |

### Clubes
| Método | Endpoint | Descripción | Rol |
|---|---|---|---|
| `POST` | `/clubs` | Crear club | Owner |
| `GET` | `/clubs` | Listar clubes aprobados | Público |
| `PATCH` | `/clubs/{id}/verify` | Aprobar/rechazar club | Admin |
| `PATCH` | `/clubs/{id}/activate` | Activar/desactivar club | Owner |

### Canchas y reservas
| Método | Endpoint | Descripción | Rol |
|---|---|---|---|
| `GET` | `/courts/available` | Buscar canchas disponibles | Player |
| `POST` | `/reservations` | Crear reserva | Player |
| `GET` | `/reservations/my` | Ver mis reservas | Player |
| `DELETE` | `/reservations/{id}` | Cancelar reserva | Player |

---

## 👩‍💻 Autora

**Camila Piergentili**  
Técnica Universitaria en Programación · Profesora de Matemática  