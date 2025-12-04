package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    @Query("SELECT r FROM ReservationEntity r " +
    "WHERE r.court.id = :idCourt " )
    List<ReservationEntity> findReservationForCountAndDay(@Param("idCourt") long idCourt);
}
