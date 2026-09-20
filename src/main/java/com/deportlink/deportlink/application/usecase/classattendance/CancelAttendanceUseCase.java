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
 * Cancela manualmente la asistencia de un alumno a una ClassSession puntual — NO modifica su
 * ClassEnrollment (sigue siendo parte del grupo fijo) ni libera la ocupación de cancha de la
 * clase (la clase sigue ocurriendo con el resto del grupo). Bloquea el grupo para coordinarse
 * con cambios de inscripción. Reincorporar un alumno devuelve sus asistencias futuras a PENDING.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelAttendanceUseCase {

    private final ClassAttendanceRepositoryPort classAttendanceRepository;
    private final ClassSessionRepositoryPort classSessionRepository;
    private final ClassSlotRepositoryPort classSlotRepository;

    @Transactional
    public ClassAttendance execute(Actor actor, Long classAttendanceId) {
        log.info("Cancelling attendance: actor={}, classAttendanceId={}", actor.id(), classAttendanceId);

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

        ClassAttendance saved = classAttendanceRepository.save(attendance.cancel());
        log.info("Attendance cancelled: id={}", saved.id());
        return saved;
    }
}
