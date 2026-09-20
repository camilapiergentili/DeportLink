package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * La cancha existe pero no está ACTIVE. Nombrada explícitamente sobre Court (a diferencia de
 * AddScheduleUseCase, que reutiliza ClubNotActivedException para este mismo chequeo — un nombre
 * que no corresponde a Court ni a Club). No se replica esa imprecisión acá: no existía ninguna
 * excepción ya nombrada correctamente para "cancha no activa", así que se agrega esta en vez de
 * propagar el nombre equivocado a código nuevo.
 */
public class CourtNotActiveException extends BusinessException {
    public CourtNotActiveException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
