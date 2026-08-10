package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.persistence.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScheduleGatewayAdapter implements ScheduleGateway {

    private final ScheduleRepository scheduleRepository;

    @Override
    public Optional<SlotConfig> findByCourtAndDay(Long courtId, DayOfWeek day) {
        return scheduleRepository.findByCourtIdAndDay(courtId, day).map(entity ->
                new SlotConfig(entity.getOpeningTime(), entity.getClosingTime(), entity.getSlotDuration())
        );
    }
}