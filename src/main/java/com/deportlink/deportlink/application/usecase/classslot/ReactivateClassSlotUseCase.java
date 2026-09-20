package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactivateClassSlotUseCase {

    private final ClassSlotRepositoryPort classSlotRepository;
    private final com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway classSlotCourtGateway;
    private final ValidateClassRecurrence validateRecurrence;
    private final ValidateClassSlotSchedule validateSchedule;
    private final com.deportlink.deportlink.application.usecase.classsession.MaintainClassSlotScheduleUseCase maintainSchedule;

    @Transactional
    public ClassSlot execute(Actor actor, Long classSlotId) {
        log.info("Reactivating class slot: actor={}, classSlotId={}", actor.id(), classSlotId);

        // Court first: reactivation competes with new reservations beyond the session horizon.
        classSlotCourtGateway.findCourtIdByClassSlotForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));
        ClassSlot slot = classSlotRepository.findByIdForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));

        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassSlotNotFoundException("No se encontró el horario");
        }

        // slot.reactivate() lanza StatusAlreadyAppliedException si ya estaba activo.
        ClassSlot active = slot.reactivate();
        // Re-valida contra la grilla vigente — el Schedule de la cancha pudo cambiar mientras el
        // ClassSlot estaba pausado (borrado y recreado con otro slotDuration). Cierra F17 en este
        // segundo punto de entrada; ver ValidateClassSlotSchedule y docs/loop/F17.md.
        validateSchedule.execute(active);
        validateRecurrence.execute(active);
        ClassSlot saved = classSlotRepository.save(active);
        maintainSchedule.execute(saved.id());
        log.info("Class slot reactivated: id={}", saved.id());
        return saved;
    }
}
