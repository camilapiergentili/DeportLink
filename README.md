# DeportLink

API para gestionar clubes, sucursales, canchas, agendas y reservas deportivas, con sistema de usuarios, verificación documental y activación de establecimientos.

## 🧩 Modelo de Dominio


- **👤 User (Clase base)**

Representa a cualquier usuario del sistema.

Herencia:

Owner (Dueño / Administrador de clubes)

Player (Jugador que realiza reservas)

🧑‍💼 Owner

El Owner administra la parte “operativa” de clubes y sucursales.

Funcionalidades:

Puede tener muchos clubes.

Puede activar/desactivar clubes, sucursales y canchas.

Puede cargar documentación para verificación.

Comparte club con otros Owners (relación muchos a muchos).

🧑‍🦽 Player

El jugador puede:

Buscar canchas disponibles.

Crear reservas.

Cancelar o consultar sus reservas.

🏢 Club

Entidad central del sistema.

Relaciones:

Un Owner puede tener varios clubes.

Un Club puede tener varios Owners.

Un Club tiene muchas sucursales.

Estados:

verificationStatus (VerificationStatus):
Verificado por el dueño de la aplicación (admin global).

PENDING

APPROVED

REJECTED

activeStatus (ActiveStatus):
Controlado por los Owners del club.

ACTIVE

DESACTIVE

🏪 Branch (Sucursal)

Una sucursal pertenece a un club.

Características:

Puede tener muchas canchas.

Debe presentar documentación.

Debe pasar por verificación.

Estados:

verificationStatus

activeStatus

🏟️ Court (Cancha)

Pertenece a una sucursal.

Relaciones:

Una sucursal tiene muchas canchas.

Una cancha pertenece a un deporte.

Un deporte puede tener muchas canchas.

Una cancha tiene muchas agendas (Schedule).

Una cancha puede tener muchas reservas.

Estado:

activeStatus: activado/desactivado por la sucursal.

🏅 Sport

Define un tipo de deporte (ej: Fútbol, Padel, Tenis).

Un deporte → muchas canchas.

🕒 Schedule (Agenda)

Representa los días y horarios de atención de una cancha.

Relación:

1 cancha → muchas agendas.

Datos típicos:

Día (LocalDate o enum)

Hora inicio

Hora fin

📅 Reservation (Reserva)

Une Player + Court.

Relación:

1 jugador → muchas reservas

1 cancha → muchas reservas

Campos:

Día de la reserva (date)

Horario (startTime)

Duración (duration)

statusReservation (StatusReservation)

PENDING, CONFIRMED, CANCELLED