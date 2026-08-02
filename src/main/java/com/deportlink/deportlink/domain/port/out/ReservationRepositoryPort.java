package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Reservation;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port de salida del dominio — define QUÉ necesita el dominio de la persistencia,
 * sin saber CÓMO está implementado (JPA, JDBC, MongoDB, en memoria).
 * <p>
 * La implementación concreta vive en infrastructure/adapter/ReservationRepositoryAdapter.
 */
public interface ReservationRepositoryPort {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(Long id);

    /**
     * Devuelve los horarios ya ocupados para una cancha en un día dado.
     * Solo incluye reservas en estado que ocupan slot (RESERVADO).
     * Retorna Set para garantizar O(1) en la verificación de disponibilidad.
     */
    Set<LocalTime> findBookedSlots(Long courtId, LocalDate day);

    List<Reservation> findByPlayerId(Long playerId);

    /**
     * Returns active reservation slots for a court on a given day of week.
     * Used by UpdateScheduleUseCase to verify existing reservations fit in the new time range.
     */
    List<ActiveSlot> findActiveByCourtAndDay(Long courtId, DayOfWeek day);

    record ActiveSlot(LocalTime startTime, Duration duration) {}
}