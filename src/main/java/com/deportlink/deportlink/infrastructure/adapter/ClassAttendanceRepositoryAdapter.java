package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.enums.ClassAttendanceStatus;
import com.deportlink.deportlink.exception.ClassAttendanceNotFoundException;
import com.deportlink.deportlink.model.entity.ClassAttendanceEntity;
import com.deportlink.deportlink.persistence.repository.ClassAttendanceRepository;
import com.deportlink.deportlink.persistence.repository.ClassAttendanceRepository.AttendanceStatusCount;
import com.deportlink.deportlink.persistence.repository.ClassSessionRepository;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ClassAttendanceRepositoryAdapter implements ClassAttendanceRepositoryPort {

    private final ClassAttendanceRepository classAttendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final PlayerRepository playerRepository;

    @Override
    public Optional<ClassAttendance> findBySessionAndPlayer(Long sessionId, Long playerId) {
        return classAttendanceRepository.findByClassSession_IdAndPlayer_Id(sessionId, playerId).map(this::toDomain);
    }

    @Override
    public ClassAttendance save(ClassAttendance attendance) {
        ClassAttendanceEntity entity = attendance.id() == null
                ? buildNewEntity(attendance)
                : updateExistingEntity(attendance);
        return toDomain(classAttendanceRepository.save(entity));
    }

    @Override
    public List<ClassAttendance> saveAll(List<ClassAttendance> attendances) {
        List<ClassAttendanceEntity> entities = attendances.stream()
                .map(a -> a.id() == null ? buildNewEntity(a) : updateExistingEntity(a))
                .toList();
        return classAttendanceRepository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ClassAttendance> findById(Long id) {
        return classAttendanceRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ClassAttendance> findByClassSessionId(Long classSessionId) {
        return classAttendanceRepository.findByClassSession_Id(classSessionId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public Map<Long, AttendanceCounts> countByStatusForSessions(Set<Long> classSessionIds) {
        if (classSessionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> counts = new HashMap<>(); // [pending, confirmed, cancelled]
        for (AttendanceStatusCount row : classAttendanceRepository.countGroupedByStatus(classSessionIds)) {
            long[] triple = counts.computeIfAbsent(row.getSessionId(), id -> new long[3]);
            int index = switch (row.getStatus()) {
                case PENDING -> 0;
                case CONFIRMED -> 1;
                case CANCELLED -> 2;
            };
            triple[index] = row.getTotal();
        }
        Map<Long, AttendanceCounts> result = new HashMap<>();
        counts.forEach((sessionId, triple) -> result.put(sessionId, new AttendanceCounts(triple[0], triple[1], triple[2])));
        return result;
    }

    private ClassAttendanceEntity buildNewEntity(ClassAttendance domain) {
        ClassAttendanceEntity entity = new ClassAttendanceEntity();
        entity.setClassSession(classSessionRepository.getReferenceById(domain.classSessionId()));
        entity.setPlayer(playerRepository.getReferenceById(domain.playerId()));
        entity.setStatus(domain.status());
        return entity;
    }

    private ClassAttendanceEntity updateExistingEntity(ClassAttendance domain) {
        ClassAttendanceEntity entity = classAttendanceRepository.findById(domain.id())
                .orElseThrow(() -> new ClassAttendanceNotFoundException("No se encontró la asistencia"));
        entity.setStatus(domain.status());
        return entity;
    }

    private ClassAttendance toDomain(ClassAttendanceEntity entity) {
        return new ClassAttendance(
                entity.getId(),
                entity.getClassSession().getId(),
                entity.getPlayer().getId(),
                entity.getStatus()
        );
    }
}
