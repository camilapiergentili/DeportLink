package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
