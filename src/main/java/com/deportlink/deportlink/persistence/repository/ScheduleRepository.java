package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<ScheduleEntity, Long> {

    @Query("""
            SELECT COUNT(r) > 0
            FROM ReservationEntity r
            WHERE r.court.id = :courtId
                AND FUNCTION('DAYOFWEEK', r.day) = :mysqlDay
            """)
    boolean existsReservationForDay(@Param("courtId") long idCourt,
                                    @Param("mysqlDay") int mysqlDay
    );

    Optional<ScheduleEntity> findByCourtIdAndDay(long idCourt, DayOfWeek day);

    List<ScheduleEntity> findByCourtId(Long courtId);

    @Query("SELECT s FROM ScheduleEntity s WHERE s.id = :id AND s.court.id = :courtId")
    Optional<ScheduleEntity> findByIdAndCourtId(@Param("id") long id, @Param("courtId") long courtId);
}
