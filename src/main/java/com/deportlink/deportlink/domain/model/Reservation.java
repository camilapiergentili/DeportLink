package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.CancellationTimeExceededException;
import com.deportlink.deportlink.exception.InvalidStatusTransitionException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Aggregate Root del dominio de reservas.
 * <p>
 * Toda transición de estado (cancelar, reprogramar) pasa por este objeto.
 * Nadie puede cambiar el status desde afuera — las reglas de negocio
 * están encapsuladas aquí, no en un servicio de 280 líneas.
 * <p>
 * Inmutable por diseño (record): las transiciones devuelven una nueva instancia.
 * Ventaja: imposible tener un estado intermedio corrupto.
 */
public record Reservation(
        Long id,
        Long courtId,
        Long playerId,
        TimeSlot timeSlot,
        StatusReservation status,
        Ticket ticket
) {

    private static final int CANCELLATION_WINDOW_HOURS = 12;

    /**
     * Crea una reserva nueva. El dominio garantiza que la fecha sea futura.
     * El id es null hasta que la infraestructura persiste y lo asigna.
     */
    public static Reservation create(Long courtId, Long playerId, TimeSlot timeSlot) {
        if (!timeSlot.isFuture()) {
            throw new InvalidTimeRangeException("La fecha seleccionada ya pasó");
        }
        return new Reservation(null, courtId, playerId, timeSlot, StatusReservation.RESERVADO, null);
    }

    /**
     * Cancela la reserva. El dominio verifica:
     * 1. Que el estado permita cancelación.
     * 2. Que no hayan pasado menos de 12 horas.
     *
     * @param now momento actual — inyectado para facilitar el testing sin mocks de reloj.
     */
    public Reservation cancel(LocalDateTime now) {
        if (status == StatusReservation.CANCELADO || status == StatusReservation.FINALIZADO) {
            throw new InvalidStatusTransitionException("La reserva no puede cancelarse en su estado actual");
        }
        long hoursUntil = ChronoUnit.HOURS.between(now, timeSlot.toDateTime());
        if (hoursUntil < CANCELLATION_WINDOW_HOURS) {
            throw new CancellationTimeExceededException(
                    "El turno no puede cancelarse con menos de 12 horas de anticipación");
        }
        return withStatus(StatusReservation.CANCELADO);
    }

    /**
     * Marca esta reserva como REPROGRAMADO (para historial).
     * El caso de uso RescheduleReservationUseCase crea una nueva RESERVADO por separado.
     */
    public Reservation markAsRescheduled() {
        if (status != StatusReservation.RESERVADO) {
            throw new InvalidStatusTransitionException("Solo una reserva RESERVADO puede reprogramarse");
        }
        return withStatus(StatusReservation.REPROGRAMADO);
    }

    public boolean belongsTo(Long aPlayerId) {
        return playerId.equals(aPlayerId);
    }

    public boolean canBeModified() {
        return status == StatusReservation.RESERVADO;
    }

    public Reservation withTicket(Ticket ticket) {
        return new Reservation(id, courtId, playerId, timeSlot, status, ticket);
    }

    public Reservation withId(Long id) {
        return new Reservation(id, courtId, playerId, timeSlot, status, ticket);
    }

    private Reservation withStatus(StatusReservation newStatus) {
        return new Reservation(id, courtId, playerId, timeSlot, newStatus, ticket);
    }
}