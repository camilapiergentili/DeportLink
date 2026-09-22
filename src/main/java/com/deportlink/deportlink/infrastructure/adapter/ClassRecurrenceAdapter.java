package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.ClassRecurrencePort;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.persistence.repository.ClassSlotRepository;
import com.deportlink.deportlink.persistence.repository.CourtOccupancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClassRecurrenceAdapter implements ClassRecurrencePort {
    private final ClassSlotRepository slots;
    private final CourtOccupancyRepository occupancy;

    public Set<LocalTime> findActiveStarts(Long courtId, DayOfWeek day) {
        return slots.findByCourt_IdAndDayOfWeekAndActiveStatus(courtId, day, ActiveStatus.ACTIVE)
                .stream().map(s -> s.getStartTime()).collect(Collectors.toSet());
    }

    public List<Long> findActiveSlotIds() {
        return slots.findIdsByActiveStatus(ActiveStatus.ACTIVE);
    }

    public boolean hasConflict(ClassSlot slot, LocalDateTime from) {
        boolean otherSchedule = slots.findByCourt_IdAndDayOfWeekAndActiveStatus(
                slot.courtId(), slot.dayOfWeek(), ActiveStatus.ACTIVE).stream()
                .anyMatch(s -> !Objects.equals(s.getId(), slot.id()) && s.getStartTime().equals(slot.startTime()));
        if (otherSchedule) return true;
        // No horizon limit: existing bookings months ahead must also be respected.
        return occupancy.findConflictingDates(slot.courtId(), slot.startTime(),
                        from.toLocalDate(), from.toLocalTime(), slot.id()).stream()
                .anyMatch(day -> day.getDayOfWeek() == slot.dayOfWeek());
    }
}
