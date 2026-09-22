package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassEnrollmentNotFoundException;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baja lógica de un alumno del grupo fijo y cancelación de sus asistencias futuras.
 * Conserva las filas y no modifica las asistencias de sesiones pasadas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemovePlayerFromClassSlotUseCase {

    private final ClassSlotRepositoryPort classSlotRepository;
    private final ClassEnrollmentRepositoryPort classEnrollmentRepository;
    private final com.deportlink.deportlink.application.usecase.classattendance.SyncFutureClassRoster syncFutureRoster;

    @Transactional
    public ClassEnrollment execute(Actor actor, Long classSlotId, Long playerId) {
        log.info("Removing player from class slot: actor={}, classSlotId={}, playerId={}",
                actor.id(), classSlotId, playerId);

        ClassSlot slot = classSlotRepository.findByIdForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));

        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassSlotNotFoundException("No se encontró el horario");
        }

        // La ownership ya se validó vía el ClassSlot — este NotFound no es un caso anti-IDOR,
        // es simplemente "ese alumno nunca perteneció a este grupo".
        ClassEnrollment enrollment = classEnrollmentRepository.findByClassSlotIdAndPlayerId(classSlotId, playerId)
                .orElseThrow(() -> new ClassEnrollmentNotFoundException("El alumno no pertenece a este horario"));

        // enrollment.deactivate() lanza StatusAlreadyAppliedException si ya estaba inactivo.
        ClassEnrollment saved = classEnrollmentRepository.save(enrollment.deactivate());
        log.info("Player removed from class slot: classSlotId={}, playerId={}", classSlotId, playerId);
        syncFutureRoster.execute(classSlotId, playerId, false);
        return saved;
    }
}
