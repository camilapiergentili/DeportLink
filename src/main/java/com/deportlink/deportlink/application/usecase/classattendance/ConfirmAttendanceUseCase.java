package com.deportlink.deportlink.application.usecase.classattendance;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassAttendanceNotFoundException;
import com.deportlink.deportlink.exception.ClassSessionNotScheduledException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirma manualmente la asistencia de un alumno — ejecutada hoy por el instructor/ADMIN; es la
 * misma acción que, en el futuro, disparará tanto "simular confirmación de WhatsApp" (el mismo
 * caso de uso, sin ningún cambio) como el autoservicio del propio Player (ver
 * ClassAttendance.belongsTo, no usado acá a propósito — pertenece a esa etapa futura, no a esta).
 * <p>
 * Idempotente: confirmar una asistencia ya CONFIRMED no lanza error, y una CANCELLED puede
 * volver a CONFIRMED — decisión de dominio ya tomada (ver ClassAttendance, Javadoc). Nunca
 * modifica el ClassEnrollment ni la ocupación de cancha, y no valida capacidad — eso solo aplica
 * al alta de un alumno al grupo (AddPlayerToClassSlotUseCase), no a cambiar el estado de una
 * asistencia ya existente. Bloquea el grupo para coordinarse con altas y bajas; en sesiones
 * futuras exige que el alumno siga inscripto antes de confirmar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmAttendanceUseCase {

    private final ClassAttendanceRepositoryPort classAttendanceRepository;
    private final ClassSessionRepositoryPort classSessionRepository;
    private final ClassSlotRepositoryPort classSlotRepository;
    private final com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort enrollments;
    private final java.time.Clock clock;

    @Transactional
    public ClassAttendance execute(Actor actor, Long classAttendanceId) {
        log.info("Confirming attendance: actor={}, classAttendanceId={}", actor.id(), classAttendanceId);

        // Resolución Attendance -> Session -> Slot: cualquier quiebre en la cadena (incluida la
        // falta de ownership del instructor) colapsa al mismo error, referido siempre al
        // identificador recibido (classAttendanceId) — mismo criterio anti-IDOR del módulo.
        // First locking read, shared with enrollment changes and session generation.
        ClassSlot slot = classSlotRepository.findByAttendanceIdForUpdate(classAttendanceId)
                .orElseThrow(() -> new ClassAttendanceNotFoundException("No se encontró la asistencia"));
        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassAttendanceNotFoundException("No se encontró la asistencia");
        }
        ClassAttendance attendance = classAttendanceRepository.findById(classAttendanceId)
                .orElseThrow(() -> new ClassAttendanceNotFoundException("No se encontró la asistencia"));

        ClassSession session = classSessionRepository.findById(attendance.classSessionId())
                .orElseThrow(() -> new ClassAttendanceNotFoundException("No se encontró la asistencia"));

        if (!session.isScheduled()) {
            throw new ClassSessionNotScheduledException(
                    "No se puede modificar la asistencia de una clase que no está programada");
        }

        // A removed player cannot take a future seat back through an old attendance.
        if (java.time.LocalDateTime.of(session.day(), session.startTime()).isAfter(java.time.LocalDateTime.now(clock))
                && enrollments.findByClassSlotIdAndPlayerId(slot.id(), attendance.playerId())
                    .filter(com.deportlink.deportlink.domain.model.ClassEnrollment::active).isEmpty()) {
            throw new com.deportlink.deportlink.exception.ClassEnrollmentNotFoundException(
                    "El alumno ya no pertenece al grupo; debe reincorporarse antes de confirmar");
        }
        ClassAttendance saved = classAttendanceRepository.save(attendance.confirm());
        log.info("Attendance confirmed: id={}", saved.id());
        return saved;
    }
}
