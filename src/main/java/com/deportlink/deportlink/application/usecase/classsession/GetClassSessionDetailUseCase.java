package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerRosterGateway;
import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ClassAttendanceStatus;
import com.deportlink.deportlink.exception.ClassSessionNotFoundException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Detalle completo de una ClassSession: los datos del resumen (cancha/nivel/capacidad resueltos
 * desde el ClassSlot — editar el horario está fuera de alcance del MVP, así que no hace falta un
 * snapshot propio acá) + el roster de alumnos. No excluye alumnos cuyo ClassEnrollment ya fue
 * dado de baja después de crear la sesión: la ClassAttendance es historial independiente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetClassSessionDetailUseCase {

    private final ClassSessionRepositoryPort classSessionRepository;
    private final ClassSlotRepositoryPort classSlotRepository;
    private final ClassAttendanceRepositoryPort classAttendanceRepository;
    private final CourtGateway courtGateway;
    private final PlayerRosterGateway playerRosterGateway;

    @Transactional(readOnly = true)
    public ClassSessionDetail execute(Actor actor, Long classSessionId) {
        ClassSession session = classSessionRepository.findById(classSessionId)
                .orElseThrow(() -> new ClassSessionNotFoundException("No se encontró la clase"));

        ClassSlot slot = classSlotRepository.findById(session.classSlotId())
                .orElseThrow(() -> new ClassSessionNotFoundException("No se encontró la clase"));

        if (!actor.canAccess(slot.instructorId())) {
            throw new ClassSessionNotFoundException("No se encontró la clase");
        }

        CourtGateway.CourtSnapshot court = courtGateway.findById(slot.courtId())
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        List<ClassAttendance> attendances = classAttendanceRepository.findByClassSessionId(classSessionId);

        // Los conteos de esta única sesión salen del roster ya cargado — no hace falta llamar
        // a countByStatusForSessions (ese batch existe para el listado de agenda, no para acá).
        long pending = attendances.stream().filter(a -> a.status() == ClassAttendanceStatus.PENDING).count();
        long confirmed = attendances.stream().filter(a -> a.status() == ClassAttendanceStatus.CONFIRMED).count();
        long cancelled = attendances.stream().filter(a -> a.status() == ClassAttendanceStatus.CANCELLED).count();

        ClassSessionSummary summary = new ClassSessionSummary(
                session.id(), session.classSlotId(), session.day(), session.startTime(), session.duration(),
                session.status(), slot.courtId(), court.name(), slot.level(), slot.capacity(),
                pending, confirmed, cancelled
        );

        // Una sola consulta batch para todos los nombres del roster — nunca una por alumno.
        Set<Long> playerIds = attendances.stream().map(ClassAttendance::playerId).collect(toSet());
        Map<Long, PlayerGateway.PlayerSnapshot> players = playerRosterGateway.findByIds(playerIds);

        List<ClassAttendanceView> attendees = attendances.stream()
                .map(attendance -> toView(attendance, players))
                .toList();

        return new ClassSessionDetail(summary, attendees);
    }

    private ClassAttendanceView toView(ClassAttendance attendance, Map<Long, PlayerGateway.PlayerSnapshot> players) {
        PlayerGateway.PlayerSnapshot player = players.get(attendance.playerId());
        String playerName = player != null ? player.firstName() + " " + player.lastName() : null;
        return new ClassAttendanceView(attendance.id(), attendance.playerId(), playerName, attendance.status());
    }
}
