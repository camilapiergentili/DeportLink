package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.model.entity.CourtEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
        SELECT c FROM CourtEntity c
        LEFT JOIN FETCH c.sport
        LEFT JOIN FETCH c.schedules
        WHERE c.branch.id = ?1
    """)
    List<CourtEntity> findByBranchIdWithEagerLoading(long branchId);

    @Query("""
        SELECT DISTINCT c FROM CourtEntity c
        LEFT JOIN FETCH c.sport
        LEFT JOIN FETCH c.schedules
        WHERE c.branch.verificationStatus = :verificationStatus
        AND c.branch.activeStatus = :activeStatus
    """)
    Page<CourtEntity> findByBranch_VerificationStatusAndBranch_ActiveStatus(
            @Param("verificationStatus") VerificationStatus verificationStatus,
            @Param("activeStatus") ActiveStatus activeStatus,
            Pageable pageable);

    Page<CourtEntity> findByBranch_IdAndActiveStatus(
            long idBranch,
            ActiveStatus activeStatus,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT c FROM CourtEntity c
        LEFT JOIN FETCH c.sport
        LEFT JOIN FETCH c.schedules
        WHERE c.branch.id = :branchId
    """)
    Page<CourtEntity> findByBranch_Id(
            @Param("branchId") long branchId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CourtEntity c WHERE c.id = :id")
    Optional<CourtEntity> findByIdForUpdate(@Param("id") long id);

    @Query("""
    SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
    FROM CourtEntity c
    JOIN c.branch b
    JOIN b.club club
    JOIN club.owners owner
    WHERE c.id = :idCourt
      AND owner.id = :idOwner
""")
    boolean existsByCourtAndOwner(
            @Param("idCourt") long idCourt,
            @Param("idOwner") long idOwner
    );
}
