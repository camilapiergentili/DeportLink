package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.ActiveStatus;
import com.deportlink.DeportLink.model.VerificationStatus;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<CourtEntity> findBySport_Id(long idSport);
}
