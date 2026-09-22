package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway;
import com.deportlink.deportlink.application.port.out.CourtOccupancyPort;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.ClassSessionAlreadyExistsException;
import com.deportlink.deportlink.exception.ClassSessionDayMismatchException;
import com.deportlink.deportlink.exception.ClassSlotNotActiveException;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.CourtNotActiveException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.CourtSlotOccupiedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Materializa la ocurrencia concreta de una fecha a partir de un ClassSlot, y genera las
 * ClassAttendance PENDING iniciales para cada alumno actualmente activo. Es reutilizado por
 * el mantenimiento automático de las próximas cuatro semanas y por la creación explícita.
 * <p>
 * <b>Orden de locks: Court → ClassSlot.</b> Court es el recurso realmente disputado entre esta
 * clase y una Reservation (ambas escriben en CourtOccupancyPort para el mismo court_id/día/hora):
 * se bloquea PRIMERO, derivándolo desde el classSlotId (ver Javadoc de ClassSlotCourtGateway),
 * antes de leer nada más — incluido el propio ClassSlot. Si el ClassSlot se leyera antes, el
 * snapshot de REPEATABLE READ de MySQL fijaría el estado de la ocupación de la cancha antes del
 * lock, dejando la misma ventana de carrera que RescheduleReservationConcurrencyTest detectó
 * originalmente para Court. Ver docs/class-management-mvp-design.md, sección 7.
 * <p>
 * <b>Límite transaccional único</b>: los 11 pasos (derivar/lockear Court, lockear ClassSlot,
 * validar estados, validar día de semana, construir la sesión, chequear duplicado, chequear
 * ocupación, leer enrollments, guardar sesión, registrar ocupación, guardar asistencias) corren
 * en la misma transacción — ninguna excepción se atrapa para devolver éxito parcial; si algo
 * falla, Spring hace rollback de todo.
 * <p>
 * Las pruebas unitarias verifican el protocolo; las de integración con MySQL y Flyway
 * verifican persistencia, rollback y concurrencia. La ocupación compartida protege coincidencias
 * exactas de cancha, fecha y hora de inicio; no detecta todos los solapamientos parciales.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateClassSessionUseCase {

    private final ClassSlotCourtGateway classSlotCourtGateway;
    private final ClassSlotRepositoryPort classSlotRepository;
    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;
    private final ClassSessionRepositoryPort classSessionRepository;
    private final CourtOccupancyPort courtOccupancyPort;
    private final ClassEnrollmentRepositoryPort classEnrollmentRepository;
    private final ClassAttendanceRepositoryPort classAttendanceRepository;

    @Transactional
    public ClassSession execute(Actor actor, Long classSlotId, LocalDate day) {
        log.info("Creating class session: actor={}, classSlotId={}, day={}", actor.id(), classSlotId, day);

        // 1) Lock de Court — PRIMERA lectura de la transacción (ver Javadoc de la clase).
        Long courtId = classSlotCourtGateway.findCourtIdByClassSlotForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));

        // 2) Lock de ClassSlot — segunda lectura. Ownership + estado propio.
        ClassSlot slot = classSlotRepository.findByIdForUpdate(classSlotId)
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));
        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassSlotNotFoundException("No se encontró el horario");
        }
        if (!slot.isActive()) {
            throw new ClassSlotNotActiveException("No se puede crear una clase: el horario está pausado");
        }

        // 3) Estados de cancha/sucursal — pueden haber cambiado desde que se creó el ClassSlot,
        // se revalidan acá igual que AddScheduleUseCase los revalida en cada alta de agenda.
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
        if (!court.isActive()) {
            throw new CourtNotActiveException("No se puede crear una clase: la cancha no está activa");
        }
        Branch branch = branchRepository.findById(court.branchId())
                .orElseThrow(() -> new BranchNotFoundException("No se encontró la sucursal"));
        if (!branch.isApproved()) {
            throw new BranchNotApprovedException("No se puede crear una clase: la sucursal no está aprobada");
        }

        // 4) Día de semana coincidente.
        if (day.getDayOfWeek() != slot.dayOfWeek()) {
            throw new ClassSessionDayMismatchException(
                    "La fecha " + day + " no es un " + slot.dayOfWeek() + ", día configurado para este horario");
        }

        // 5) Factory de dominio — valida que la fecha/hora no sea pasada (ClassSession.create).
        ClassSession session = ClassSession.create(classSlotId, day, slot.startTime(), slot.duration());

        // 6) Sesión duplicada para el mismo ClassSlot+fecha.
        if (classSessionRepository.existsByClassSlotIdAndDay(classSlotId, day)) {
            throw new ClassSessionAlreadyExistsException("Ya existe una clase para ese horario en la fecha " + day);
        }

        // 7) Conflicto de cancha — contra una Reservation o contra otra ClassSession (ver
        // Javadoc de CourtOccupancyPort para las limitaciones reales de esta garantía hoy).
        if (courtOccupancyPort.existsOccupancy(courtId, day, slot.startTime())) {
            throw new CourtSlotOccupiedException("La cancha ya está ocupada en ese horario");
        }

        // 8) Alumnos activos del grupo fijo, para generar sus asistencias iniciales.
        List<ClassEnrollment> activeEnrollments = classEnrollmentRepository.findActiveByClassSlotId(classSlotId);

        // 9) Guardar la sesión — a partir de acá se usa el id devuelto por save(), nunca uno propio.
        ClassSession saved = classSessionRepository.save(session);

        // 10) Registrar la ocupación vinculada al id real de la sesión persistida.
        courtOccupancyPort.registerForClassSession(saved.id(), courtId, day, slot.startTime());

        // 11) Una ClassAttendance PENDING por cada alumno activo — sin alumnos, sesión sin asistencias.
        if (!activeEnrollments.isEmpty()) {
            List<ClassAttendance> pendingAttendances = activeEnrollments.stream()
                    .map(enrollment -> ClassAttendance.createPending(saved.id(), enrollment.playerId()))
                    .toList();
            classAttendanceRepository.saveAll(pendingAttendances);
        }

        log.info("Class session created: id={}, attendances={}", saved.id(), activeEnrollments.size());
        return saved;
    }
}
