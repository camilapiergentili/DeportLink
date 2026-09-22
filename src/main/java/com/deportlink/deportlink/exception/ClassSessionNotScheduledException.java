package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** No se puede confirmar/cancelar una asistencia cuya ClassSession ya no está SCHEDULED. */
public class ClassSessionNotScheduledException extends BusinessException {
    public ClassSessionNotScheduledException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
