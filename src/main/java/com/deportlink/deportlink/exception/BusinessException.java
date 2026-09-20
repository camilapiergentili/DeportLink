package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * Clase base de toda excepción de negocio del dominio. Cada subclase concreta declara, en su
 * propio constructor, el {@link HttpStatus} que le corresponde — GlobalExceptionHandler ya no
 * necesita enumerar cada excepción a mano: un único @ExceptionHandler(BusinessException.class)
 * lee el status desde la excepción misma. Agregar una excepción de negocio nueva no requiere
 * tocar GlobalExceptionHandler (principio abierto/cerrado).
 */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;

    protected BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected BusinessException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
