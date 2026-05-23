package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.StatusReservation;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    @Query("""
    SELECT r FROM ReservationEntity r
    WHERE r.court.id = :idCourt
      AND r.status IN :activeStatuses
""")
    List<ReservationEntity> findActiveByCourt(
            @Param("idCourt") long idCourt,
            @Param("activeStatuses") List<StatusReservation> activeStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT r FROM ReservationEntity r
    WHERE r.court.id = :idCourt
      AND r.day = :day
      AND r.status IN :activeStatuses
""")
    List<ReservationEntity> findActiveByCourtAndDay(
            @Param("idCourt") long idCourt,
            @Param("day") LocalDate day,
            @Param("activeStatuses") List<StatusReservation> activeStatuses
    );
}
