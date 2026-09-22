package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ClassAttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ClassAttendanceRepository extends JpaRepository<ClassAttendanceEntity, Long> {
    java.util.Optional<ClassAttendanceEntity> findByClassSession_IdAndPlayer_Id(Long sessionId, Long playerId);

    List<ClassAttendanceEntity> findByClassSession_Id(Long classSessionId);

    // Agregación por GROUP BY — una sola ida a la base para N sesiones, la consulta batch en sí
    // misma (ver docs/class-management-stage-1c-persistence-design.md, sección 4).
    @Query("""
        SELECT a.classSession.id AS sessionId, a.status AS status, COUNT(a) AS total
        FROM ClassAttendanceEntity a
        WHERE a.classSession.id IN :sessionIds
        GROUP BY a.classSession.id, a.status
    """)
    List<AttendanceStatusCount> countGroupedByStatus(@Param("sessionIds") Set<Long> sessionIds);

    interface AttendanceStatusCount {
        Long getSessionId();
        com.deportlink.deportlink.enums.ClassAttendanceStatus getStatus();
        Long getTotal();
    }
}
