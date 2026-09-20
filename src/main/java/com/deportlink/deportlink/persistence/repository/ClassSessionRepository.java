package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ClassSessionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSessionEntity, Long> {
    @Query("""
        SELECT s FROM ClassSessionEntity s WHERE s.classSlot.id = :slotId
        AND s.status = com.deportlink.deportlink.enums.ClassSessionStatus.SCHEDULED
        AND (s.sessionDate > :day OR (s.sessionDate = :day AND s.startTime > :time))
        ORDER BY s.sessionDate, s.startTime, s.id
        """)
    List<ClassSessionEntity> findScheduledAfter(@Param("slotId") Long slotId,
            @Param("day") LocalDate day, @Param("time") java.time.LocalTime time);

    boolean existsByClassSlot_IdAndSessionDate(Long classSlotId, LocalDate sessionDate);

    // "to" nullable = sin límite superior (ver GetClassSessionsByInstructorUseCase). @Query
    // explícita en vez de dos derived queries condicionales — más claro que resolver la
    // presencia/ausencia de "to" del lado de Spring Data.
    @Query("""
        SELECT s FROM ClassSessionEntity s
        WHERE s.classSlot.instructor.id = :instructorId
          AND s.sessionDate >= :from
          AND (:to IS NULL OR s.sessionDate <= :to)
    """)
    List<ClassSessionEntity> findByInstructorAndDateRange(
            @Param("instructorId") Long instructorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
