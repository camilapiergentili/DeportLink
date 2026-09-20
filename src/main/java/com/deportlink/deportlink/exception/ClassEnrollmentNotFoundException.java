package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * No existe un ClassEnrollment para ese (classSlotId, playerId). Se lanza solo después de validar
 * ownership del ClassSlot — no es un caso anti-IDOR (el actor ya probó ser dueño del horario), es
 * simplemente "ese alumno nunca perteneció a este grupo".
 */
public class ClassEnrollmentNotFoundException extends BusinessException {
    public ClassEnrollmentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
