# Etapa 1D — contrato HTTP y acceso del profesor

Estado: APROBADO E IMPLEMENTADO; ver validación al final. Fecha: 2026-09-09.
El usuario aprobó el contrato antes de implementar. Las rutas ya existen en el código.
Complementa el MVP, la persistencia 1C y class-management-recurrence.md.

## 1. Resultado para el profesor

Inicia sesión, ve su agenda, configura una clase semanal una sola vez, administra
el grupo y registra asistencias. La creación sigue generando cuatro próximas fechas;
el mantenimiento existente las repone. El panel nunca tiene que generar semanas.

## 2. Reutilización comprobada en el código

- POST /api/auth/login ya recibe email/password y devuelve {"token":"..."}.
- JwtFilter carga el usuario de la base y UserMain construye ROLE_<rol>.
- InstructorEntity hereda de UserEntity mediante JOINED: instructorId y userId coinciden.
- Ya existen Actor, ActorRole y controles de pertenencia en los casos de uso.
- Los casos de uso de horarios, sesiones y asistencia ya contienen las transacciones.
- GlobalExceptionHandler ya traduce BusinessException y devuelve timestamp, status,
  error, message, path y method. Los errores del filtro JWT tienen otro formato hoy.
- No existe alta de instructores, endpoint de identidad ni controllers de clases.
- Las consultas actuales no alcanzan para seleccionar alumnos o ver el grupo fijo
  independientemente de una sesión. Son ampliaciones explícitas de lectura en esta etapa.

## 3. Identidad y autorización propuestas

Reutilizar Authorization: Bearer <token>. No cambiar el contrato del login.
Agregar GET /api/me para cualquier usuario autenticado: {id, firstName, lastName, role}.
No ubicarlo bajo /api/auth/**, que hoy está permitido sin autenticación.

Agregar un adaptador CurrentActor que construya Actor desde UserMain y sus authorities
verificadas. No aceptar actorId, actorRole ni instructorId del body en rutas del profesor.
El rol por sí solo no sustituye los controles de pertenencia de los casos de uso.

- /api/instructor/** exige INSTRUCTOR; el instructor objetivo siempre es el autenticado.
- PLAYER y OWNER reciben 403 en estas rutas.
- Un instructor que intenta operar sobre un horario/sesión/asistencia ajeno recibe 404,
  igual que si no existiera. La pertenencia se verifica antes de consultar sus alumnos.
- ADMIN da de alta instructores. No se agrega todavía un panel administrativo de clases;
  el soporte ADMIN que ya tienen los casos de uso se conserva.
- No se implementa autoservicio de asistencia del PLAYER.

Alta propuesta: POST /api/admin/instructors, ADMIN solamente. Body:
{firstName, lastName, email, password, phone}. Rol INSTRUCTOR asignado por el servidor.
201 con {id, firstName, lastName, email, phone, role}; Location: /api/admin/instructors/{id}.
Agregar GET de esa ruta para ADMIN, con el mismo DTO, para resolver Location.
Validar como UserRequestDto, usar PasswordEncoder existente y comprobar email en users,
no solo instructors. Duplicado: 409 incluso si pertenece a otro rol. Alta de ambas tablas
en una transacción. Nunca devolver ni registrar contraseña/hash.
No convertir cuentas PLAYER/OWNER existentes ni crear un ADMIN automáticamente.
La entrega de credenciales se coordina fuera de la aplicación; invitaciones y recuperación
de contraseña no se implementan aquí. Es una propuesta de alta controlada, no registro público.

## 4. Rutas del panel

Base: /api/instructor. Todas exigen INSTRUCTOR.

| Método y ruta | Resultado | Implementación |
| --- | --- | --- |
| GET /class-slots | 200, Slot[] propios, activos y pausados | GetClassSlotsByInstructorUseCase |
| POST /class-slots | 201, Slot y Location al detalle | CreateClassSlotUseCase, con generación automática existente |
| GET /class-slots/{slotId} | 200, SlotDetail | Nueva lectura con pertenencia y grupo fijo |
| PUT /class-slots/{slotId}/pause | 200, Slot | PauseClassSlotUseCase |
| PUT /class-slots/{slotId}/reactivate | 200, Slot | ReactivateClassSlotUseCase |
| POST /class-slots/{slotId}/players | 200, Enrollment; body {playerId} | AddPlayerToClassSlotUseCase; puede reincorporar |
| DELETE /class-slots/{slotId}/players/{playerId} | 200, Enrollment inactivo | RemovePlayerFromClassSlotUseCase |
| GET /class-sessions?from=YYYY-MM-DD&to=YYYY-MM-DD | 200, SessionSummary[] | GetClassSessionsByInstructorUseCase |
| GET /class-sessions/{sessionId} | 200, SessionDetail | GetClassSessionDetailUseCase |
| PUT /class-attendances/{attendanceId}/confirm | 200, Attendance | ConfirmAttendanceUseCase |
| PUT /class-attendances/{attendanceId}/cancel | 200, Attendance | CancelAttendanceUseCase |

PUT de transición no requiere body. Repetir pause/reactivate mantiene el conflicto
de estado existente; confirm/cancel mantienen la idempotencia del dominio.
No exponer CreateClassSessionUseCase como paso obligatorio ni el scheduler mediante HTTP.

Agenda HTTP: from por defecto hoy según Clock; to por defecto from + 27 días.
Ambos inclusivos; rechazar to < from y rangos de más de 93 días con 400.
Es un límite del endpoint, sin alterar el contrato interno del caso de uso.
Consultar meses lejanos no genera sesiones: puede devolver [], aunque el horario
semanal siga activo y protegiendo reservas. El panel distingue horario fijo de sesión.

## 5. Datos y ejemplos para el frontend

Fechas YYYY-MM-DD; horas HH:mm:ss; durationMinutes entero positivo; IDs positivos.
Capacidad obligatoria 1..4. dayOfWeek usa nombres Java (MONDAY...SUNDAY).
level: PRINCIPIANTE, INTERMEDIO, AVANZADO. Estado del horario: ACTIVE/INACTIVE.
La capa HTTP convierte minutos a Duration y usa DTOs: no serializar entidades JPA.
Propuesta de validación HTTP: minutos enteros, sin segundos fraccionarios; rechazar
una clase que termine después de medianoche. No confundir esto con validar todos
los solapamientos: se conserva la protección existente por hora de inicio exacta.
Horas de negocio en la zona configurada del servidor; desplegar explícitamente con
America/Argentina/Buenos_Aires. No convertir estos campos locales con toISOString().

Crear jueves a las 18:

```json
{
  "courtId": 12,
  "dayOfWeek": "THURSDAY",
  "startTime": "18:00:00",
  "durationMinutes": 60,
  "level": "INTERMEDIO",
  "capacity": 4
}
```

Slot: {id, instructorId, courtId, dayOfWeek, startTime, durationMinutes, level, capacity, status}.
SlotDetail: {slot: Slot, players: [{playerId, playerName, active}]}.
players incluye solo inscripciones activas del grupo fijo; active será true.
Enrollment: {id, classSlotId, playerId, active}.
Attendance: {id, classSessionId, playerId, status}.
SessionDetail: {summary: SessionSummary, attendees: [{attendanceId, playerId, playerName, status}]}.
playerName puede ser null según el gateway existente; mostrar una etiqueta de reemplazo.

SessionSummary (ejemplo ilustrativo, no datos reales):

```json
{
  "sessionId": 101,
  "classSlotId": 20,
  "day": "2026-09-10",
  "startTime": "18:00:00",
  "durationMinutes": 60,
  "status": "SCHEDULED",
  "courtId": 12,
  "courtName": "Cancha 2",
  "level": "INTERMEDIO",
  "capacity": 4,
  "pendingCount": 2,
  "confirmedCount": 1,
  "cancelledCount": 1,
  "occupancy": 3,
  "availableSpots": 1
}
```

Mapear occupancy y availableSpots explícitamente al DTO: los métodos del record actual
no garantizan por sí solos esos campos JSON. availableSpots es informativo, no habilita
reemplazos temporales ni altera el límite del grupo fijo.

## 6. Selección de cancha y alumnos — lecturas nuevas propuestas

- GET /api/instructor/courts?branchId=<id>&page=0&size=20 devuelve
  {items:[{courtId,courtName,branchId,branchName}],page,size,hasNext}.
  Solo canchas activas de sucursales aprobadas, orden por courtId; size 1..50.
  Reutilizar datos existentes sin devolver owners, usuarios ni entidades completas.
  No hay relación instructor-cancha en el modelo actual: esta propuesta conserva
  la posibilidad de elegir cualquier cancha elegible. Asignar canchas por profesor
  sería otra regla de negocio y necesitaría modelado adicional.
- POST /api/instructor/player-lookup con {email}: búsqueda exacta, email obligatorio
  válido; 200 con {playerId,playerName} o 404 si no hay PLAYER. Body para no colocar
  emails en la URL; no registrar ese body. No permite listar toda la base de alumnos.
  Esta ruta permite al instructor resolver una cuenta conocida por email; no demuestra
  pertenencia a su grupo. La incorporación sigue pasando por el caso de uso autorizado.
- Para alumnos ya inscriptos, usar SlotDetail, no volver a pedir el email.
- El MVP usa cuentas PLAYER existentes. Si el alumno no tiene cuenta, debe completar
  el registro existente (que hoy exige dirección). Crear alumnos solo con nombre/teléfono
  sería una ampliación diferente; no inventar emails ni contraseñas para cubrirla.

## 7. Errores y comportamiento del panel

Conservar ErrorResponse actual, sin exigir que el frontend interprete nombres Java.
400: DTO inválido, enum/hora/fecha inválidos, JSON malformado, rango incorrecto.
401: falta token o es inválido/expiró. 403: rol no permitido.
404: recurso inexistente/ajeno, alumno o inscripción inexistentes.
409: cancha ocupada, grupo completo, alumno ya activo, transición de estado repetida.
Mantener el HttpStatus específico de cada BusinessException existente; validar todos
los casos en tests antes de cerrar el contrato implementado.

Agregar handlers explícitos de JSON ilegible y conversión de parámetros a 400: hoy
el catch-all puede devolver 500. Unificar también los errores de JwtFilter con el
formato estándar, conservando 401. No filtrar detalles SQL ni credenciales.
Ante 409 mostrar message y refrescar agenda/grupo. Ante 401 volver al login.
No reintentar automáticamente POST de creación tras un timeout: primero recargar
horarios; no se propone aquí una clave de idempotencia.

## 8. Archivos y orden de implementación propuestos

1. DTOs request/response de clases y mappers, contrato de validación y ejemplos OpenAPI.
2. CurrentActor en security/resolver, MeController y alta/consulta ADMIN de instructor:
   RegisterInstructorUseCase, puerto de persistencia y adapter; reutilizar InstructorEntity,
   InstructorRepository, UserRepository y PasswordEncoder. Sin nuevas tablas previstas.
3. GetClassSlotDetailUseCase y lecturas de canchas/player-lookup con puertos/proyecciones
   mínimos y adapters. Control de pertenencia en aplicación, no solo en controller.
4. InstructorClassSlotController, InstructorClassSessionController,
   InstructorClassAttendanceController e InstructorLookupController bajo controller/.
5. Ajustar WebConfig para resolver Actor y GlobalExceptionHandler/JwtFilter para errores.
   Mantener login público y registro PLAYER; no cambiar globalmente quién accede a esas rutas.
   Métodos HTTP propuestos ya están incluidos en CORS: no hace falta agregar PATCH.
6. Pruebas HTTP, seguridad y recorrido completo; publicar OpenAPI verificado y actualizar
   este documento con diferencias reales. La lista exacta de DTOs se deriva de la sección 5.

## 9. Criterios para cerrar 1D

- Alta ADMIN persiste users/instructors atómicamente, codifica password y rechaza email
  duplicado entre roles; login real del instructor devuelve un JWT utilizable.
- Tests HTTP con filtros y seguridad habilitados: sin token 401; PLAYER/OWNER 403;
  dos instructores aislados en listados, detalle, alumnos, pausa y asistencias.
- Manipular IDs o mandar actor/rol en JSON nunca cambia la identidad autenticada.
- Recorrido MySQL/Flyway: alta, login, horario semanal, cuatro fechas, inscripción,
  confirmación, baja y reingreso; siguen funcionando rollback y protección de reservas.
- JSON tiene exactamente los campos documentados, sin password/hash/email de alumnos.
- Fechas/enums/body inválidos devuelven 400; conflictos 409 y recursos ajenos 404.
- Las consultas GET no crean sesiones y el panel puede cargar selects y grupo fijo.
- Suite completa pasa con Docker. Los 454 tests del 07/09 son referencia histórica,
  no una ejecución de hoy. Los resultados de implementación se registran al final.

## 10. Límite de esta entrega

Contrato aprobado por el usuario, incluidos alta ADMIN, búsqueda por email exacto
y selección de cualquier cancha elegible descritas arriba.
Quedan fuera panel visual, WhatsApp, pagos, invitados sin cuenta, conversión entre roles,
edición de horario/duración, cancelación de sesión completa y despliegue de producción.

## 11. Implementación y precisiones del contrato

- Alta y consulta se agruparon en InstructorAccountUseCase, con InstructorAccountPort y
  InstructorAccountAdapter. No hubo necesidad de agregar entidades ni migraciones.
- InstructorRequestDto aplica las mismas validaciones básicas de nombre/email/password
  que el registro existente, sin heredar confirmPassword: no era parte del body aprobado.
  Nombres/email/teléfono tienen máximo 255 caracteres; password 8..72 caracteres y máximo
  72 bytes UTF-8 por el encoder. El rol no se acepta del request.
- GetClassSlotDetailUseCase valida pertenencia antes de leer inscripciones o nombres.
  InstructorLookupUseCase/Port/Adapter agregan las dos lecturas del selector.
- ClassApiResponses agrupa DTOs explícitos y conversiones. El DTO de creación rechaza
  fracciones y strings en durationMinutes/capacity; rechaza segundos en hora de inicio.
- El límite de paginación es page 0..1000000, size 1..50 para evitar overflow del offset.
- El rango de agenda HTTP inválido tiene una excepción 400 propia; no se cambió
  InvalidTimeRangeException de dominio (422) para no afectar contratos existentes.
- 403 también puede indicar cancha inactiva o sucursal no aprobada; se conservan esas
  BusinessException. Ningún rechazo de pertenencia a clases se cambia a 403.
- El resolver Actor se registra en WebConfig y los controllers ocultan sus argumentos
  de identidad en OpenAPI. Se conserva @PreAuthorize y la validación de pertenencia interna.
- Se agregaron handlers 400 para JSON/parámetros inválidos. El log de validación no imprime
  valores rechazados, para evitar registrar contraseñas. JwtFilter usa el ErrorResponse
  estándar de JwtAuthenticationEntryPoint ante tokens inválidos.
- Se agregó springdoc-openapi-starter-webmvc-ui 2.8.17: las rutas Swagger estaban permitidas
  en seguridad pero faltaba la dependencia. La matriz de compatibilidad está enlazada
  en class-management-panel-integration.md. OpenAPI incluye esquema bearerAuth.
- Primera prueba MySQL: 8 escenarios, 2 fallos (422 en rango HTTP y OpenAPI inexistente).
  Corregidos; segunda ejecución: 8 escenarios, todos pasando. Se agregó además un caso
  de revocación de rol en base de datos para la suite final.

Guía para el panel: class-management-panel-integration.md.

## 12. Validación final — 2026-09-09

`./mvnw -q -o test`: **463 tests, 0 failures, 0 errors, 0 skipped**, 71 suites.
Proceso Maven finalizado con exit code 0. Incluye las 454 pruebas anteriores y los
9 escenarios nuevos de InstructorApiIntegrationTest, con MockMvc, filtros JWT reales,
seguridad de métodos y MySQL Testcontainers con Flyway habilitado y ddl-auto=validate.
Se verificaron altas, login, aislamiento entre profesores, rechazo de roles, rol actualizado
en base, selectores, validación, conflictos, generación semanal y recorrido de asistencias.

OpenAPI exportado desde /v3/api-docs durante esa ejecución a
class-management-api.openapi.json. Se comprobó que los POST de alta devuelvan 201 en
la especificación y que Actor no figure como parámetro del cliente.
No se ejecutaron migraciones ni altas de cuentas contra una base de producción.
