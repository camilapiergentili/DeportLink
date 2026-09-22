package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** La cancha tiene ClassSlot asociados — no se puede eliminar (mismo criterio que CourtHasReservationsException). */
public class CourtHasClassSlotsException extends BusinessException {
    public CourtHasClassSlotsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
