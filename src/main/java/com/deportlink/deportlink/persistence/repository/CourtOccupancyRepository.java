package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.enums.OccupancySourceType;
import com.deportlink.deportlink.model.entity.CourtOccupancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;

@Repository
public interface CourtOccupancyRepository extends JpaRepository<CourtOccupancyEntity, Long> {

    @org.springframework.data.jpa.repository.Query("""
        SELECT o.occupiedDay FROM CourtOccupancyEntity o
        WHERE o.court.id = :courtId AND o.startTime = :time
          AND (o.occupiedDay > :day OR (o.occupiedDay = :day AND o.startTime > :nowTime))
          AND (:slotId IS NULL OR o.sourceType <> com.deportlink.deportlink.enums.OccupancySourceType.CLASS_SESSION
               OR o.sourceId NOT IN (SELECT s.id FROM ClassSessionEntity s WHERE s.classSlot.id = :slotId))
        """)
    java.util.List<LocalDate> findConflictingDates(
            @org.springframework.data.repository.query.Param("courtId") Long courtId,
            @org.springframework.data.repository.query.Param("time") LocalTime time,
            @org.springframework.data.repository.query.Param("day") LocalDate day,
            @org.springframework.data.repository.query.Param("nowTime") LocalTime nowTime,
            @org.springframework.data.repository.query.Param("slotId") Long slotId);

    boolean existsByCourt_IdAndOccupiedDayAndStartTime(Long courtId, LocalDate occupiedDay, LocalTime startTime);

    // Libera por origen, nunca por coordenadas — ver Javadoc de CourtOccupancyPort.
    void deleteBySourceTypeAndSourceId(OccupancySourceType sourceType, Long sourceId);
}
