package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.enums.ClassAttendanceStatus;

/**
 * Fila del roster de una ClassSession para GetClassSessionDetailUseCase. Expone únicamente lo
 * necesario para el panel — nunca email, teléfono, contraseña ni el PlayerEntity completo.
 * playerName es {@code null} si el Player ya no pudo resolverse vía PlayerRosterGateway (no
 * debería ocurrir en operación normal, pero no se descarta la fila por eso — la ClassAttendance
 * es historial independiente del estado actual del Player).
 */
public record ClassAttendanceView(Long attendanceId, Long playerId, String playerName, ClassAttendanceStatus status) {}
