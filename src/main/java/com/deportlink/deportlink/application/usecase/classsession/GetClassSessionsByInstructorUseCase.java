package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort.AttendanceCounts;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.InstructorNotFoundException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Agenda del instructor para el panel — no genera ClassSession al consultar (eso sigue siendo
 * manual, vía CreateClassSessionUseCase). Enriquece cada ClassSession con datos de su ClassSlot y
 * conteos de asistencia en, como máximo, un puñado de consultas batch — nunca una por sesión.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetClassSessionsByInstructorUseCase {

    private final ClassSessionRepositoryPort classSessionRepository;
    private final ClassSlotRepositoryPort classSlotRepository;
    private final ClassAttendanceRepositoryPort classAttendanceRepository;
    private final CourtGateway courtGateway;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ClassSessionSummary> execute(Actor actor, Long instructorId, LocalDate from, LocalDate to) {
        if (!actor.canAccess(instructorId)) {
            throw new InstructorNotFoundException("No se encontró el instructor");
        }

        // Default explícito de esta etapa: sin "from", se arranca desde hoy; sin "to", sin límite
        // superior. Clock inyectable para que "hoy" sea determinístico en tests.
        LocalDate effectiveFrom = from != null ? from : LocalDate.now(clock);
        if (to != null && to.isBefore(effectiveFrom)) {
            throw new InvalidTimeRangeException("La fecha 'hasta' no puede ser anterior a la fecha 'desde'");
        }

        List<ClassSession> sessions = classSessionRepository.findByInstructor(instructorId, effectiveFrom, to);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Set<Long> slotIds = sessions.stream().map(ClassSession::classSlotId).collect(toSet());
        Map<Long, ClassSlot> slotsById = classSlotRepository.findAllByIds(slotIds).stream()
                .collect(toMap(ClassSlot::id, slot -> slot));

        Set<Long> sessionIds = sessions.stream().map(ClassSession::id).collect(toSet());
        Map<Long, AttendanceCounts> countsBySession = classAttendanceRepository.countByStatusForSessions(sessionIds);

        Map<Long, CourtGateway.CourtSnapshot> courtCache = new HashMap<>();

        return sessions.stream()
                .sorted(Comparator.comparing(ClassSession::day)
                        .thenComparing(ClassSession::startTime)
                        .thenComparing(ClassSession::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(session -> toSummary(session, slotsById, countsBySession, courtCache))
                .toList();
    }

    private ClassSessionSummary toSummary(ClassSession session, Map<Long, ClassSlot> slotsById,
                                           Map<Long, AttendanceCounts> countsBySession,
                                           Map<Long, CourtGateway.CourtSnapshot> courtCache) {
        ClassSlot slot = slotsById.get(session.classSlotId());
        if (slot == null) {
            throw new ClassSlotNotFoundException("No se encontró el horario de la clase " + session.id());
        }

        CourtGateway.CourtSnapshot court = courtCache.computeIfAbsent(slot.courtId(), courtId ->
                courtGateway.findById(courtId)
                        .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha")));

        AttendanceCounts counts = countsBySession.getOrDefault(session.id(), new AttendanceCounts(0, 0, 0));

        return new ClassSessionSummary(
                session.id(), session.classSlotId(), session.day(), session.startTime(), session.duration(),
                session.status(), slot.courtId(), court.name(), slot.level(), slot.capacity(),
                counts.pending(), counts.confirmed(), counts.cancelled()
        );
    }
}
