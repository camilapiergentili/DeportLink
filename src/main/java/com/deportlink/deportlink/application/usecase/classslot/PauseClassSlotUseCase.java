package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausa un ClassSlot — no toca su ClassEnrollment (grupo fijo) ni ninguna ClassSession/
 * ClassAttendance ya creada. Solo impide crear ClassSession nuevas mientras esté pausado
 * (ver CreateClassSessionUseCase).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PauseClassSlotUseCase {

    private final ClassSlotRepositoryPort classSlotRepository;

    @Transactional
    public ClassSlot execute(Actor actor, Long classSlotId) {
        log.info("Pausing class slot: actor={}, classSlotId={}", actor.id(), classSlotId);

        // Lock pesimista — primera lectura de la transacción. Comparte el lock con
        // AddPlayer/RemovePlayer/Reactivate/CreateClassSession para serializar mutaciones
        // concurrentes sobre el mismo ClassSlot.
        ClassSlot slot = classSlotRepository.findByIdForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));

        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassSlotNotFoundException("No se encontró el horario");
        }

        // slot.pause() lanza StatusAlreadyAppliedException si ya estaba pausado — regla de dominio.
        ClassSlot saved = classSlotRepository.save(slot.pause());
        log.info("Class slot paused: id={}", saved.id());
        return saved;
    }
}
