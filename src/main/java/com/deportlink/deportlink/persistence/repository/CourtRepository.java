package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import com.deportlink.deportlink.model.entity.CourtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourtRepository extends JpaRepository<CourtEntity, Long> {

    Optional<CourtEntity> findByNameAndBranchIdAndSportId(String name, long idBranch, long idSport);
    Optional<CourtEntity> findByIdAndBranch_Id(long idCourt, long idBranch);
    List<CourtEntity> findByBranch_VerificationStatusAndBranch_ActiveStatus(
            VerificationStatus verificationStatus,
            ActiveStatus activeStatus);

    List<CourtEntity> findByBranch_IdAndSport_Id(long idBranch, long idSport);

    @Query("SELECT DISTINCT c FROM CourtEntity c " +
    "LEFT JOIN FETCH c.schedules " +
    "WHERE c.id = :idCourt")
    Optional<CourtEntity> findByCourtWithSchedule(@Param("idCourt") long idCourt);

    List<CourtEntity> findBySport_Id(long idSport);
}
