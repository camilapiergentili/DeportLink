package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.port.out.ClassRecurrencePort;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.exception.CourtSlotOccupiedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
@RequiredArgsConstructor
public class ValidateClassRecurrence {
    private final ClassRecurrencePort recurrence;
    private final Clock clock;

    /** Court must already be locked by the caller, before the first consistent read. */
    public void execute(ClassSlot slot) {
        if (recurrence.hasConflict(slot, LocalDateTime.now(clock))) {
            throw new CourtSlotOccupiedException(
                    "El horario semanal coincide con otro horario activo o una ocupación futura de la cancha");
        }
    }
}
