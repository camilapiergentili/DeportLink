package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {

    List<BranchEntity> findAllByClubId(long clubId);

    @Query("SELECT b FROM BranchEntity b WHERE b.club.id = :idClub " +
    "AND b.activeStatus = :activeStatus " +
    "AND b.verificationStatus = :verificationStatus")
    List<BranchEntity> findActiveAndApprovedByClubId(@Param("idClub") long idClub,
                                                     @Param("activeStatus") ActiveStatus activeStatus,
                                                     @Param("verificationStatus")VerificationStatus verificationStatus);

    boolean existsByAddressAndClub(AddressEntity address, ClubEntity club);
    boolean existsByNameIgnoreCaseAndClub(String name, ClubEntity club);
}
