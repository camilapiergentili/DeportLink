package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.enums.StatusReservation;
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
import java.util.Set;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    // Devuelve solo los horarios de inicio de los slots que ocupan cancha (sin cargar entidades completas).
    // Usado por ReservationRepositoryAdapter.findBookedSlots — O(1) en la verificación gracias al Set.
    @Query("""
        SELECT r.startTime FROM ReservationEntity r
        WHERE r.court.id = :courtId
          AND r.day = :day
          AND r.status IN :occupyingStatuses
    """)
    Set<LocalTime> findStartTimesByCourtAndDay(
            @Param("courtId") Long courtId,
            @Param("day") LocalDate day,
            @Param("occupyingStatuses") List<StatusReservation> occupyingStatuses
    );

    List<ReservationEntity> findByPlayer_Id(Long playerId);

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
