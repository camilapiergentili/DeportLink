package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GetScheduleByDayUseCase {

    private final ScheduleRepositoryPort scheduleRepository;

    @Transactional(readOnly = true)
    public Schedule execute(Long courtId, LocalDate day) {
        return scheduleRepository.findByCourtIdAndDay(courtId, day.getDayOfWeek())
                .orElseThrow(() -> new ScheduleNotFoundException("No se encontró agenda para el día seleccionado"));
    }
}