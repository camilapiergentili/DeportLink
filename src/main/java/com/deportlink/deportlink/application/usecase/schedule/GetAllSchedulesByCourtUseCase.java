package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllSchedulesByCourtUseCase {

    private final CourtRepositoryPort courtRepository;
    private final ScheduleRepositoryPort scheduleRepository;

    @Transactional(readOnly = true)
    public List<Schedule> execute(Long courtId) {
        courtRepository.findById(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        List<Schedule> schedules = scheduleRepository.findAllByCourtId(courtId);
        if (schedules.isEmpty()) {
            throw new ScheduleNotFoundException("La cancha aun no tiene agenda disponible");
        }
        return schedules;
    }
}