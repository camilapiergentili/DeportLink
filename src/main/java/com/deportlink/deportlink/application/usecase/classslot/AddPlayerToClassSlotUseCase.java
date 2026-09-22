package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassSlotFullException;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.PlayerAlreadyEnrolledException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Suma un Player al grupo fijo de un ClassSlot. Deliberadamente NO valida que el ClassSlot esté
 * activo: pausar un horario impide generar ClassSession nuevas (ver CreateClassSessionUseCase),
 * pero no impide administrar su grupo — el instructor puede seguir armando/ajustando el roster
 * mientras el horario está pausado (ver spec de esta etapa, sección 6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddPlayerToClassSlotUseCase {

    private final ClassSlotRepositoryPort classSlotRepository;
    private final ClassEnrollmentRepositoryPort classEnrollmentRepository;
    private final com.deportlink.deportlink.application.usecase.classattendance.SyncFutureClassRoster syncFutureRoster;
    private final PlayerGateway playerGateway;

    @Transactional
    public ClassEnrollment execute(Actor actor, Long classSlotId, Long playerId) {
        log.info("Adding player to class slot: actor={}, classSlotId={}, playerId={}",
                actor.id(), classSlotId, playerId);

        // Lock pesimista sobre el ClassSlot — primera lectura de la transacción. Mismo lock que
        // usan Pause/Reactivate/CreateClassSession, para serializar altas de alumnos concurrentes
        // entre sí y contra esos otros flujos.
        ClassSlot slot = classSlotRepository.findByIdForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));

        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassSlotNotFoundException("No se encontró el horario");
        }

        playerGateway.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("No se encontró el jugador"));

        Optional<ClassEnrollment> existing = classEnrollmentRepository.findByClassSlotIdAndPlayerId(classSlotId, playerId);
        if (existing.isPresent() && existing.get().active()) {
            throw new PlayerAlreadyEnrolledException("El alumno ya pertenece a este horario");
        }

        int activeCount = classEnrollmentRepository.countActiveByClassSlotId(classSlotId);
        if (!slot.hasRoom(activeCount)) {
            throw new ClassSlotFullException("El horario alcanzó su capacidad máxima de " + slot.capacity() + " alumnos");
        }

        // Si había un enrollment dado de baja antes, se reactiva conservando su id — evita
        // duplicar la fila (y con ella, el historial de ClassAttendance vinculado indirectamente
        // por playerId+classSlotId quedaría igual de consistente en cualquier caso).
        ClassEnrollment toSave = existing
                .map(ClassEnrollment::activate)
                .orElseGet(() -> ClassEnrollment.create(classSlotId, playerId));

        ClassEnrollment saved = classEnrollmentRepository.save(toSave);
        log.info("Player added to class slot: classSlotId={}, playerId={}, enrollmentId={}",
                classSlotId, playerId, saved.id());
        syncFutureRoster.execute(classSlotId, playerId, true);
        return saved;
    }
}
