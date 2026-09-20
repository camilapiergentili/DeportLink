package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.ClassAttendance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ClassAttendanceRepositoryPort {
    Optional<ClassAttendance> findBySessionAndPlayer(Long sessionId, Long playerId);

    ClassAttendance save(ClassAttendance attendance);

    /** Alta en lote de las asistencias PENDING iniciales de una ClassSession recién creada. */
    List<ClassAttendance> saveAll(List<ClassAttendance> attendances);

    Optional<ClassAttendance> findById(Long id);

    /** Roster completo de una sesión — incluye alumnos cuyo ClassEnrollment ya fue dado de baja. */
    List<ClassAttendance> findByClassSessionId(Long classSessionId);

    /**
     * Conteo por estado, para N sesiones en una sola consulta — GetClassSessionsByInstructorUseCase
     * necesita esto para cada fila del listado de agenda; una consulta por sesión sería el mismo
     * problema de N+1 que se evita explícitamente para el roster de alumnos en el detalle. Una
     * sesión sin ninguna entrada en el mapa resultante se interpreta como 0/0/0 (sin asistencias).
     */
    Map<Long, AttendanceCounts> countByStatusForSessions(Set<Long> classSessionIds);

    record AttendanceCounts(long pending, long confirmed, long cancelled) {
        public long occupancy() { return pending + confirmed; }
    }
}
