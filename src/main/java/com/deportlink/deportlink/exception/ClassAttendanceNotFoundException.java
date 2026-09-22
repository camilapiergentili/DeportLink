package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * Se usa para "la asistencia no existe" y para cualquier quiebre en la resolución
 * Attendance → Session → Slot (incluida la falta de ownership del instructor sobre el Slot) —
 * mismo criterio anti-IDOR aplicado en todo el módulo, referido siempre al identificador que
 * recibió el caso de uso (classAttendanceId).
 */
public class ClassAttendanceNotFoundException extends BusinessException {
    public ClassAttendanceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
