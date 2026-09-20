package com.deportlink.deportlink.application.usecase.classattendance;

import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.enums.ClassAttendanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Caller holds ClassSlot. Same transaction as membership change, preserving historical rows. */
@Service
@RequiredArgsConstructor
public class SyncFutureClassRoster {
    private final ClassSessionRepositoryPort sessions;
    private final ClassAttendanceRepositoryPort attendances;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(Long slotId, Long playerId, boolean active) {
        for (var session : sessions.findScheduledAfter(slotId, LocalDateTime.now(clock))) {
            var existing = attendances.findBySessionAndPlayer(session.id(), playerId);
            if (active) {
                if (existing.isEmpty()) {
                    attendances.save(ClassAttendance.createPending(session.id(), playerId));
                } else if (existing.get().status() == ClassAttendanceStatus.CANCELLED) {
                    var old = existing.get();
                    // Explicit re-enrollment starts pending; no other player's response changes.
                    attendances.save(new ClassAttendance(old.id(), old.classSessionId(), old.playerId(),
                            ClassAttendanceStatus.PENDING));
                }
            } else {
                existing.ifPresent(a -> attendances.save(a.cancel()));
            }
        }
    }
}
