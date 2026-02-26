package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    @Query("SELECT r FROM ReservationEntity r " +
    "WHERE r.court.id = :idCourt " )
    List<ReservationEntity> findReservationForCountAndDay(@Param("idCourt") long idCourt);

    @Query("""
        SELECT r FROM ReservationEntity r
        WHERE r.court.id = :idCourt
          AND r.day = :day
    """)
    List<ReservationEntity> findByCourtAndDay(
            @Param("idCourt") long idCourt,
            @Param("day") LocalDate day
    );

    @Query("""
    SELECT COUNT(r) > 0 FROM ReservationEntity r
    WHERE r.court.id = :courtId
      AND r.day = :day
      AND r.startTime = :startTime
      AND r.status NOT IN ('CANCELADO', 'FINALIZADO')
    """)
    boolean existsConflict(
            @Param("courtId") long courtId,
            @Param("day") LocalDate day,
            @Param("startTime") LocalTime startTime
    );
}
