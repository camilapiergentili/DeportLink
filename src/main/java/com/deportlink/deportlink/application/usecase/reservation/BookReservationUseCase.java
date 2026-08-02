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
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
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
public class BookReservationUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final CourtGateway courtGateway;
    private final PlayerGateway playerGateway;
    private final ScheduleGateway scheduleGateway;

    @Transactional
    public Reservation execute(Long courtId, Long playerId, LocalDate day, LocalTime startTime) {
        log.info("Booking: courtId={}, playerId={}, day={}, time={}", courtId, playerId, day, startTime);

        // Lock pessimista sobre la cancha — primero, antes de cualquier lectura de slots.
        // Garantiza que dos requests concurrentes para el mismo slot no pasen
        // la validación de disponibilidad simultáneamente (phantom read fix).
        CourtSnapshot court = courtGateway.findByIdForUpdate(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        PlayerSnapshot player = playerGateway.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("No se encontró el jugador"));

        SlotConfig slotConfig = scheduleGateway.findByCourtAndDay(courtId, day.getDayOfWeek())
                .orElseThrow(() -> new ScheduleNotFoundException("No hay agenda disponible para ese día"));

        if (!slotConfig.isValidSlot(startTime)) {
            throw new SlotNotAvailableException("El horario no corresponde a un turno válido");
        }

        Set<LocalTime> booked = reservationRepository.findBookedSlots(courtId, day);
        if (booked.contains(startTime)) {
            throw new SlotNotAvailableException("El horario ya está reservado");
        }

        TimeSlot timeSlot = new TimeSlot(day, startTime, slotConfig.slotDuration());

        // Reservation.create() valida que la fecha sea futura — regla de dominio, no del servicio.
        Reservation reservation = Reservation.create(courtId, playerId, timeSlot);

        // Precio: duración en horas × precio por hora. Simple por ahora;
        // si el negocio agrega descuentos o tarifas diferenciadas, esto crece
        // hacia un PricingPolicy sin tocar el caso de uso.
        double price = timeSlot.durationInHours() * court.pricePerHour();

        Ticket ticket = Ticket.issue(
                player.firstName(), player.lastName(),
                court.name(), court.sportName(),
                court.branchName(), court.branchAddress(),
                price
        );

        Reservation saved = reservationRepository.save(reservation.withTicket(ticket));
        log.info("Reservation booked: id={}", saved.id());
        return saved;
    }
}