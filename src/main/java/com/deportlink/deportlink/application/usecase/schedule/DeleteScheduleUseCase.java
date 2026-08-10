package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;

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

        int mysqlDay = mapJavaDayToMySQL(schedule.day());
        if (scheduleRepository.existsReservationForDay(courtId, mysqlDay)) {
            throw new IllegalArgumentException("No se puede eliminar, ya que existen reservas para ese dia");
        }

        scheduleRepository.delete(scheduleId);
        log.info("Schedule deleted: scheduleId={}", scheduleId);
    }

    private int mapJavaDayToMySQL(DayOfWeek day) {
        return (day.getValue() % 7) + 1;
    }
}