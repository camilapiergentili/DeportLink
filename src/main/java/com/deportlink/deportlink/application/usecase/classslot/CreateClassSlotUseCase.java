package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.InstructorGateway;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.CourtNotActiveException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.InstructorNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el horario semanal y sus cuatro próximas sesiones en una única transacción.
 * Valida conflictos futuros antes de guardarlo y reutiliza CreateClassSessionUseCase
 * para generar cada sesión con su ocupación y su grupo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateClassSlotUseCase {

    private final InstructorGateway instructorGateway;
    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;
    private final ClassSlotRepositoryPort classSlotRepository;
    private final ValidateClassRecurrence validateRecurrence;
    private final ValidateClassSlotSchedule validateSchedule;
    private final com.deportlink.deportlink.application.usecase.classsession.MaintainClassSlotScheduleUseCase maintainSchedule;

    @Transactional
    public ClassSlot execute(Actor actor, CreateClassSlotCommand command) {
        log.info("Creating class slot: actor={}, instructorId={}, courtId={}",
                actor.id(), command.instructorId(), command.courtId());

        // Un INSTRUCTOR solo puede crear para sí mismo; un ADMIN puede elegir cualquier
        // instructor. Se colapsa "no autorizado" con "no existe" (mismo criterio anti-IDOR que
        // el resto del módulo) — ver Javadoc de InstructorNotFoundException.
        if (!actor.canAccess(command.instructorId())) {
            throw new InstructorNotFoundException("No se encontró el instructor");
        }
        // CourtGateway.CourtSnapshot (el usado por Reservation) NO expone estado activo/aprobado
        // — se usan los ports de dominio Court/Branch, mismo patrón que AddScheduleUseCase.
        Court court = courtRepository.findByIdForUpdate(command.courtId())
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
        instructorGateway.findById(command.instructorId())
                .orElseThrow(() -> new InstructorNotFoundException("No se encontró el instructor"));
        if (!court.isActive()) {
            throw new CourtNotActiveException("No se puede crear una clase: la cancha no está activa");
        }

        Branch branch = branchRepository.findById(court.branchId())
                .orElseThrow(() -> new BranchNotFoundException("No se encontró la sucursal"));
        if (!branch.isApproved()) {
            throw new BranchNotApprovedException("No se puede crear una clase: la sucursal no está aprobada");
        }

        // Capacidad y duración las valida el dominio (ClassSlot.create) — no se duplican acá.
        ClassSlot slot = ClassSlot.create(command.instructorId(), command.courtId(), command.dayOfWeek(),
                command.startTime(), command.duration(), command.level(), command.capacity());

        // Ata startTime/duration a la grilla de Schedule de la cancha — cierra F17 (ver
        // ValidateClassSlotSchedule y docs/loop/F17.md). Antes de validateRecurrence: si el horario
        // ni siquiera es válido contra la grilla, no tiene sentido evaluar conflictos con él.
        validateSchedule.execute(slot);
        validateRecurrence.execute(slot);
        ClassSlot saved = classSlotRepository.save(slot);
        maintainSchedule.execute(saved.id());
        log.info("Class slot created: id={}", saved.id());
        return saved;
    }
}
