package com.deportlink.deportlink.application.port.out;

import com.deportlink.deportlink.domain.model.ClassSlot;
import java.time.*;
import java.util.List;
import java.util.Set;

/** Queries for weekly starts, including dates beyond the materialized agenda. */
public interface ClassRecurrencePort {
    Set<LocalTime> findActiveStarts(Long courtId, DayOfWeek day);
    List<Long> findActiveSlotIds();
    /** Caller holds Court then ClassSlot (if existing); own sessions are not conflicts. */
    boolean hasConflict(ClassSlot slot, LocalDateTime from);
}
