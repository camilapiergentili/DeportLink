package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** One transaction per schedule, never one transaction for all instructors.
 * Called by trusted application services, not an HTTP entry point.
 */
@Service
@RequiredArgsConstructor
public class MaintainClassSlotScheduleUseCase {
    private final ClassSlotCourtGateway courts;
    private final ClassSlotRepositoryPort slots;
    private final ClassSessionRepositoryPort sessions;
    private final CreateClassSessionUseCase createSession;
    private final Clock clock;

    @Transactional
    public int execute(Long slotId) {
        // Lock before any consistent read; shares the order used by manual creation.
        courts.findCourtIdByClassSlotForUpdate(slotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));
        var slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));
        if (!slot.isActive()) return 0;
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate day = now.toLocalDate().with(TemporalAdjusters.nextOrSame(slot.dayOfWeek()));
        if (!LocalDateTime.of(day, slot.startTime()).isAfter(now)) day = day.plusWeeks(1);
        int created = 0;
        // Four upcoming occurrences, including today only if it has not started.
        for (int week = 0; week < 4; week++, day = day.plusWeeks(1)) {
            if (!sessions.existsByClassSlotIdAndDay(slotId, day)) {
                createSession.execute(new Actor(slot.instructorId(), ActorRole.INSTRUCTOR), slotId, day);
                created++;
            }
        }
        return created;
    }
}
