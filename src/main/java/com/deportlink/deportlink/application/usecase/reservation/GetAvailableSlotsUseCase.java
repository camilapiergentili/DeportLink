package com.deportlink.deportlink.application.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetAvailableSlotsUseCase {

    private final CourtGateway courtGateway;
    private final ScheduleGateway scheduleGateway;
    private final ReservationRepositoryPort reservationRepository;

    // Read-only: no pessimistic lock needed — result is advisory (slots can change after the call).
    // The actual enforcement of availability happens in BookReservationUseCase with the lock.
    @Transactional(readOnly = true)
    public List<LocalTime> execute(Long courtId, LocalDate day) {
        log.info("Getting available slots: courtId={}, day={}", courtId, day);

        courtGateway.findById(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        SlotConfig slotConfig = scheduleGateway.findByCourtAndDay(courtId, day.getDayOfWeek())
                .orElseThrow(() -> new ScheduleNotFoundException("No hay agenda disponible para ese día"));

        Set<LocalTime> booked = reservationRepository.findBookedSlots(courtId, day);

        // generateSlots() lives in SlotConfig (application layer) rather than here
        // so that both this use case and any future scheduling logic share the same algorithm.
        List<LocalTime> available = slotConfig.generateSlots().stream()
                .filter(slot -> !booked.contains(slot))
                .toList();

        log.info("Available slots for courtId={}, day={}: {}", courtId, day, available.size());
        return available;
    }
}