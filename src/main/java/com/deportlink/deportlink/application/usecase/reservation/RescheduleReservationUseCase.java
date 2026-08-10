package com.deportlink.deportlink.application.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.Ticket;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.exception.ReservationNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RescheduleReservationUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final CourtGateway courtGateway;
    private final PlayerGateway playerGateway;
    private final ScheduleGateway scheduleGateway;

    /**
     * Reprogramar = marcar la reserva vieja como REPROGRAMADO + crear una nueva RESERVADO.
     * Ambas operaciones en la misma transacción: si falla la segunda, la primera hace rollback.
     * Así el jugador nunca queda sin cancha por un error a mitad del proceso.
     *
     * Alternativa descartada: exponer solo la transición de estado y que el cliente llame
     * Cancel + Book por separado. Problema: dos requests independientes no son atómicos —
     * otro jugador puede ocupar el nuevo slot entre ambas llamadas.
     */
    @Transactional
    public Reservation execute(Long reservationId, Long playerId, LocalDate newDay, LocalTime newStartTime) {
        log.info("Rescheduling: reservationId={}, playerId={}, newDay={}, newTime={}",
                reservationId, playerId, newDay, newStartTime);

        PlayerSnapshot player = playerGateway.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("No se encontró el jugador"));

        Reservation existing = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("No se encontró la reserva"));

        if (!existing.belongsTo(playerId)) {
            throw new ReservationNotFoundException("No se encontró la reserva");
        }

        // Lock sobre la cancha del nuevo slot antes de verificar disponibilidad.
        // Misma estrategia que BookReservationUseCase — sin el lock, dos reprogramaciones
        // concurrentes hacia el mismo slot pueden pasar la validación simultáneamente.
        CourtSnapshot court = courtGateway.findByIdForUpdate(existing.courtId())
                .orElseThrow(() -> new ReservationNotFoundException("La cancha de la reserva no existe"));

        SlotConfig slotConfig = scheduleGateway.findByCourtAndDay(existing.courtId(), newDay.getDayOfWeek())
                .orElseThrow(() -> new ScheduleNotFoundException("No hay agenda disponible para ese día"));

        if (!slotConfig.isValidSlot(newStartTime)) {
            throw new SlotNotAvailableException("El horario no corresponde a un turno válido");
        }

        Set<LocalTime> booked = reservationRepository.findBookedSlots(existing.courtId(), newDay);
        if (booked.contains(newStartTime)) {
            throw new SlotNotAvailableException("El horario ya está reservado");
        }

        // markAsRescheduled() valida que el estado actual sea RESERVADO — regla de dominio.
        Reservation rescheduled = reservationRepository.save(existing.markAsRescheduled());

        TimeSlot newSlot = new TimeSlot(newDay, newStartTime, slotConfig.slotDuration());
        Reservation newReservation = Reservation.create(existing.courtId(), playerId, newSlot);

        double price = newSlot.durationInHours() * court.pricePerHour();
        Ticket ticket = Ticket.issue(
                player.firstName(), player.lastName(),
                court.name(), court.sportName(),
                court.branchName(), court.branchAddress(),
                price
        );

        Reservation saved = reservationRepository.save(newReservation.withTicket(ticket));
        log.info("Reservation rescheduled: oldId={}, newId={}", rescheduled.id(), saved.id());
        return saved;
    }
}