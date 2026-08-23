package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.ScheduleHasReservationsException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteScheduleUseCase {

    private final ScheduleRepositoryPort scheduleRepository;

    @Transactional
    public void execute(Long scheduleId, Long courtId) {
        log.info("Deleting schedule: scheduleId={}, courtId={}", scheduleId, courtId);

        Schedule schedule = scheduleRepository.findByIdAndCourtId(scheduleId, courtId)
                .orElseThrow(() -> new ScheduleNotFoundException("No se encontró la agenda"));

        if (scheduleRepository.existsReservationForDay(courtId, schedule.day())) {
            throw new ScheduleHasReservationsException("No se puede eliminar, ya que existen reservas para ese dia");
        }

        scheduleRepository.delete(scheduleId);
        log.info("Schedule deleted: scheduleId={}", scheduleId);
    }
}