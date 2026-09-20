package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * La cancha ya está ocupada en ese día/horario exacto — por una Reservation o por otra
 * ClassSession (ver CourtOccupancyPort). Ver docs/class-management-mvp-design.md, sección 9.
 */
public class CourtSlotOccupiedException extends BusinessException {
    public CourtSlotOccupiedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
