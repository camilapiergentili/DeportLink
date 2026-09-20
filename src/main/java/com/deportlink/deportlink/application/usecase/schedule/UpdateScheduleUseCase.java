package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.InvalidReservationDataException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import com.deportlink.deportlink.exception.ReservationNotUpdateException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateScheduleUseCase {

    private final ScheduleRepositoryPort scheduleRepository;
    private final ReservationRepositoryPort reservationRepository;

    @Transactional
    public void execute(Long scheduleId, Long courtId, String openingNew, String closingNew) {
        log.info("Updating schedule: scheduleId={}, courtId={}", scheduleId, courtId);

        Schedule schedule = scheduleRepository.findByIdAndCourtId(scheduleId, courtId)
                .orElseThrow(() -> new ScheduleNotFoundException("No se encontró la agenda"));

        LocalTime opening = LocalTime.parse(openingNew);
        LocalTime closing = LocalTime.parse(closingNew);

        if (opening.isAfter(closing)) {
            throw new InvalidTimeRangeException("El horario de inicio no puede ser posterior al horario de fin");
        }

        List<ReservationRepositoryPort.ActiveSlot> active =
                reservationRepository.findActiveByCourtAndDay(courtId, schedule.day());

        for (ReservationRepositoryPort.ActiveSlot slot : active) {
            if (slot.startTime() == null || slot.duration() == null) {
                throw new InvalidReservationDataException("Una reserva tiene datos incompletos");
            }
            LocalTime end = slot.startTime().plusMinutes(slot.duration().toMinutes());
            if (slot.startTime().isBefore(opening) || end.isAfter(closing)) {
                throw new ReservationNotUpdateException("Los horarios reservados no estan dentro del nuevo rango horario");
            }
        }

        Schedule updated = new Schedule(schedule.id(), schedule.courtId(), schedule.day(),
                opening, closing, schedule.slotDuration());
        scheduleRepository.save(updated);
        log.info("Schedule updated: scheduleId={}", scheduleId);
    }
}