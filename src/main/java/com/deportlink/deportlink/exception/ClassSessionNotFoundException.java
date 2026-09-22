package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * Se usa tanto para "la clase no existe" como para "existe pero pertenece a otro instructor" —
 * mismo criterio anti-IDOR aplicado en todo el módulo (ver ClassSlotNotFoundException).
 */
public class ClassSessionNotFoundException extends BusinessException {
    public ClassSessionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
