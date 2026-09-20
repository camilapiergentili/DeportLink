package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * Capacidad de un ClassSlot fuera del rango válido (cero, negativa, o por encima del máximo
 * del negocio para el MVP). Mismo criterio que NegativePriceException/InvalidTimeRangeException:
 * un valor numérico de dominio fuera de rango es un 422, no un 400 de validación de request.
 */
public class InvalidCapacityException extends BusinessException {
    public InvalidCapacityException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
