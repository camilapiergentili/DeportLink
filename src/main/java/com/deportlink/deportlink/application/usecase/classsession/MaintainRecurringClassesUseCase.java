package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.application.port.out.ClassRecurrencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintainRecurringClassesUseCase {
    private final ClassRecurrencePort recurrence;
    private final MaintainClassSlotScheduleUseCase maintain;

    public Report execute() {
        int created = 0;
        Map<Long, String> failures = new LinkedHashMap<>();
        for (Long id : recurrence.findActiveSlotIds()) {
            try {
                // The proxied call commits/rolls back before processing another schedule.
                created += maintain.execute(id);
            } catch (RuntimeException failure) {
                failures.put(id, failure.getClass().getSimpleName());
                log.error("Could not maintain class slot {}. Will retry on next run.", id, failure);
            }
        }
        return new Report(created, Map.copyOf(failures));
    }
    public record Report(int created, Map<Long, String> failures) {}
}
