package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.ScheduleEntity;
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
}
