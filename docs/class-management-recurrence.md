# Clases semanales automáticas

Ampliación aprobada después de 1C. Sustituye la creación exclusivamente manual del MVP:
el profesor configura una sola vez el horario semanal y el backend mantiene cuatro
ocurrencias futuras. No hay límite de vigencia del horario.

## Comportamiento
- Crear o reactivar valida conflictos y genera las cuatro sesiones dentro de la misma
  transacción. Ante un error, no se guarda una configuración parcialmente aplicada.
- Un proceso al arrancar y diariamente completa las cuatro próximas fechas de cada horario
  activo. Omite sesiones existentes, no reinicia asistencias ni crea fechas pasadas.
- Cada horario se procesa en una transacción independiente. Fallos se registran con el id del
  horario, se devuelven en el reporte de mantenimiento y se reintentan en la próxima ejecución.
- Pausar detiene la generación, pero conserva las sesiones y ocupaciones ya existentes.
- Agregar un alumno crea asistencias PENDING en las sesiones futuras SCHEDULED. Reincorporar
  reutiliza una asistencia CANCELLED y la lleva a PENDING; no resetea confirmaciones existentes.
- Quitar un alumno cancela sus asistencias futuras. Las pasadas y las de otros alumnos
  permanecen intactas. No se puede reconfirmar una asistencia futura de un alumno fuera del grupo.
- Reservar, reprogramar y consultar disponibilidad consideran los horarios recurrentes activos
  incluso más allá de las cuatro semanas materializadas.
- Un horario nuevo o reactivado se rechaza si hay otro horario activo idéntico o una ocupación
  futura en el mismo día semanal y hora. Las sesiones propias no bloquean la reactivación.

## Concurrencia y alcance
Activar un horario y reservar compiten por Court como primera lectura bloqueante.
Reactivar/generar usa Court → ClassSlot; grupo y asistencia usan ClassSlot. Las consultas de
disponibilidad son orientativas; la comprobación definitiva se hace dentro de la transacción.
Se conserva la limitación de coincidencia exacta de hora de inicio; no se detectan todos los
solapamientos parciales ni clases que atraviesen medianoche.

## Operación
La configuración classes.recurrence.enabled habilita el proceso automático (por defecto true).
classes.recurrence.cron tiene como valor por defecto "0 5 0 * * *" (00:05, zona JVM).
El Clock y el proceso diario usan la misma zona de la aplicación. Los tests deshabilitan el
proceso de fondo y ejecutan los casos de uso explícitamente para evitar interferencias.
Si la aplicación estuvo apagada, el arranque completa fechas futuras; nunca inventa asistencia
de clases pasadas. Cambios por escrituras directas en BD fuera de la aplicación no están cubiertos.

No agrega controllers, panel, WhatsApp, pagos ni cancelación de sesiones completas.

## Validación ejecutada — 2026-09-07

`./mvnw -q -o test`: 454 tests, 0 failures, 0 errors, 0 skipped (70 suites).
Incluye MySQL real en Testcontainers, migraciones Flyway, rollback y concurrencia.
Se agregaron 20 tests para recurrencia, reposición del horizonte, sincronización de alumnos,
conflictos más allá de cuatro semanas y aislamiento de fallos del mantenimiento.
Las carreras reserva/clase ahora comprueban la activación de un horario pausado frente
a una reserva: un horario ya activo protege la fecha incluso sin sesión materializada.
