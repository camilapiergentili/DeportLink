# Conexión del panel del profesor

Contrato de implementación de Etapa 1D. Las rutas son relativas a la URL del backend.
El diseño y los DTOs completos están en class-management-stage-1d-api-design.md.
Validado el 2026-09-09: 463 tests pasaron, sin fallas, errores ni omitidos.

## Inicio de sesión

POST /api/auth/login con {"email":"...","password":"..."} devuelve {"token":"..."}.
Enviar Authorization: Bearer <token> en las demás operaciones.
GET /api/me devuelve {id,firstName,lastName,role}; mostrar el panel cuando role sea INSTRUCTOR.
Ocultar botones según el rol no sustituye los controles del backend.
El administrador crea profesores mediante POST /api/admin/instructors; no hay registro
público de profesor ni conversión automática de cuentas existentes.

## Carga del panel

1. GET /api/instructor/class-slots devuelve horarios semanales propios, incluidos pausados.
2. GET /api/instructor/class-sessions devuelve las sesiones desde hoy hasta dentro de 27 días.
   Para otra ventana pasar from y to, con hasta 93 días inclusive.
3. GET /api/instructor/class-slots/{id} devuelve el horario y su grupo fijo actual.
4. GET /api/instructor/class-sessions/{id} devuelve summary y attendees con attendanceId.

No inferir que un horario dejó de existir porque no hay sesiones dentro de un rango lejano.
La generación mantiene cuatro próximas ocurrencias y no se dispara por consultar agenda.

## Crear un horario

GET /api/instructor/courts?branchId=12&page=0&size=20 devuelve opciones paginadas.
branchId es opcional; hasNext indica si seguir consultando. size: 1..50; page: 0..1000000.
POST /api/instructor/class-slots con:

```json
{"courtId":12,"dayOfWeek":"THURSDAY","startTime":"18:00:00","durationMinutes":60,"level":"INTERMEDIO","capacity":4}
```

201 devuelve el horario y Location a su detalle. instructorId lo asigna el servidor.
No enviar actorId/actorRole ni usar un instructorId del navegador para autorizar operaciones.
Después de crear, volver a cargar agenda para mostrar las cuatro fechas generadas.
POST no tiene reintento automático seguro tras un timeout: consultar antes los horarios.

## Grupo y asistencias

POST /api/instructor/player-lookup con {email} encuentra un PLAYER existente y devuelve
solo {playerId,playerName}. No hay listado global de alumnos ni alta de invitados.
Para inscribir: POST /api/instructor/class-slots/{slotId}/players con {playerId}.
Para quitar: DELETE /api/instructor/class-slots/{slotId}/players/{playerId}.
Ambos devuelven la inscripción (200); el backend sincroniza las asistencias futuras.

PUT /api/instructor/class-attendances/{attendanceId}/confirm o /cancel, sin body.
Usar attendanceId del detalle de sesión, no playerId ni enrollmentId.
Refrescar el detalle y resumen tras cambiar asistencia. Los estados son
PENDING, CONFIRMED y CANCELLED. Los cupos informativos no permiten reemplazos temporales.

PUT /api/instructor/class-slots/{slotId}/pause o /reactivate, sin body.
Pausar mantiene las sesiones existentes. Si ya estaba en ese estado devuelve 409.
No ofrecer editar horario/duración ni cancelar la sesión entera: no son funciones de esta API.

## Validación y errores

- Horas locales HH:mm:ss sin conversión UTC; inicio en minutos enteros.
- durationMinutes: entero positivo, hasta 1440, sin terminar después de medianoche.
- capacity: entero 1..4. level: PRINCIPIANTE/INTERMEDIO/AVANZADO.
- 400: corregir datos. 401: volver al login. 403: rol no permitido o cancha/sucursal
  no habilitada según la regla de negocio existente. 404: recurso no disponible.
- 409: mostrar message y refrescar información; puede ser cupo, duplicado o conflicto.
- ErrorResponse: {timestamp,status,error,message,path,method}. No interpretar clases Java.
- availableSpots y occupancy se envían explícitamente como números en SessionSummary.

## Documentación ejecutable y desarrollo

Con el backend iniciado, Swagger UI está en /swagger-ui/index.html y OpenAPI JSON en
/v3/api-docs. Usar Authorize con el token del profesor para probar las rutas protegidas.
La especificación exportada de la ejecución verificada queda en class-management-api.openapi.json.
springdoc 2.8.17 se agregó porque antes solo estaban permitidas las URLs, sin dependencia
que las implementara. Compatibilidad Boot 3.5 / springdoc 2.8 según
[la matriz oficial](https://springdoc.org/v2/).

Configurar CORS_ALLOWED_ORIGINS con el origen real del panel; no cambiar a wildcard.
La zona de operación prevista es America/Argentina/Buenos_Aires, configurada en la JVM.
No se cambió la plataforma de despliegue ni se crearon cuentas en producción.
