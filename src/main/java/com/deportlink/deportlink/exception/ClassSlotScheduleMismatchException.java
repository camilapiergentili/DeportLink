package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * El horario/duración de un {@code ClassSlot} no coincide con la grilla de turnos ({@code Schedule})
 * configurada para esa cancha y día — ver F17 (docs/software-review-2026-09-09.md) y
 * docs/loop/F17.md. Mantener esa igualdad es lo que hace que la detección de conflicto de
 * {@code CourtOccupancyPort}/{@code ClassRecurrencePort} (coincidencia EXACTA de {@code start_time},
 * no solapamiento de intervalos) siga siendo una garantía válida: es la misma propiedad de la que ya
 * depende {@code Reservation} (toda reserva de una cancha/día comparte la misma duración, la del
 * {@code Schedule} vigente).
 */
public class ClassSlotScheduleMismatchException extends BusinessException {
    public ClassSlotScheduleMismatchException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
