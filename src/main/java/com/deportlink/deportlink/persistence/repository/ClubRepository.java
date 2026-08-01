package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.model.entity.ClubEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<ClubEntity, Long> {

    Optional<ClubEntity> findByCuit(String cuit);
    Optional<ClubEntity> findByLegalName(String legalName);

    List<ClubEntity> findByVerificationStatusAndActiveStatus(
            VerificationStatus verificationStatus,
            ActiveStatus activeStatus
    );

    @Query("""
        SELECT DISTINCT c FROM ClubEntity c
        LEFT JOIN FETCH c.owners
        LEFT JOIN FETCH c.branches b
        LEFT JOIN FETCH b.courts
        WHERE c.verificationStatus = ?1 AND c.activeStatus = ?2
    """)
    List<ClubEntity> findApprovedWithEagerLoading(
            VerificationStatus verificationStatus,
            ActiveStatus activeStatus
    );

    @Query("""
        SELECT c FROM ClubEntity c
        LEFT JOIN FETCH c.owners
        LEFT JOIN FETCH c.branches b
        LEFT JOIN FETCH b.courts
        WHERE c.verificationStatus = ?1 AND c.activeStatus = ?2
    """)
    Page<ClubEntity> findApprovedPaginated(
            VerificationStatus verificationStatus,
            ActiveStatus activeStatus,
            Pageable pageable
    );

    boolean existsByIdAndOwners_Id(long idClub, long idOwner);
}
