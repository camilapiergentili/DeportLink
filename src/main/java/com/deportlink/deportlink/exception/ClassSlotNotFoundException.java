package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * Se usa tanto para "el horario no existe" como para "existe pero pertenece a otro instructor" —
 * mismo criterio anti-IDOR que ReservationNotFoundException: un INSTRUCTOR no debe poder
 * distinguir, por la respuesta, entre un id inexistente y uno ajeno.
 */
public class ClassSlotNotFoundException extends BusinessException {
    public ClassSlotNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
