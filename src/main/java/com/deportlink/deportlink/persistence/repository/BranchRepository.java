package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    boolean existsByIdAndClub_Owners_Id(long idBranch, long idOwner);
    boolean existsByClub_Id(Long clubId);

    // Clean Architecture adapter queries (no entity references needed)
    boolean existsByNameIgnoreCaseAndClub_Id(String name, Long clubId);

    @Query("SELECT COUNT(b) > 0 FROM BranchEntity b WHERE b.club.id = :clubId " +
           "AND b.address.streetName = :streetName AND b.address.number = :number " +
           "AND b.address.city = :city AND b.address.province = :province " +
           "AND b.address.postalCode = :postalCode")
    boolean existsByAddressFieldsAndClubId(
            @Param("clubId") Long clubId,
            @Param("streetName") String streetName,
            @Param("number") int number,
            @Param("city") String city,
            @Param("province") String province,
            @Param("postalCode") int postalCode
    );

    List<BranchEntity> findByNameContainingIgnoreCaseAndVerificationStatusAndActiveStatus(
            String name,
            VerificationStatus verificationStatus,
            ActiveStatus activeStatus
    );

    @Query("""
        SELECT DISTINCT b FROM BranchEntity b
        JOIN b.courts c
        WHERE c.sport.id = :sportId
          AND b.verificationStatus = :vs
          AND b.activeStatus = :as
          AND c.activeStatus = :courtStatus
    """)
    List<BranchEntity> findApprovedBySport(
            @Param("sportId") Long sportId,
            @Param("vs") VerificationStatus vs,
            @Param("as") ActiveStatus as,
            @Param("courtStatus") ActiveStatus courtStatus
    );

    @Query(value = """
        SELECT b.* FROM branches b
        JOIN address a ON b.address_id = a.id
        WHERE b.verification_status = 'APPROVED'
          AND b.active_status = 'ACTIVE'
          AND (6371 * acos(
                LEAST(1.0,
                  cos(radians(:lat)) * cos(radians(a.latitude))
                  * cos(radians(a.longitude) - radians(:lng))
                  + sin(radians(:lat)) * sin(radians(a.latitude))
                )
              )) <= :radiusKm
        ORDER BY (6371 * acos(
                LEAST(1.0,
                  cos(radians(:lat)) * cos(radians(a.latitude))
                  * cos(radians(a.longitude) - radians(:lng))
                  + sin(radians(:lat)) * sin(radians(a.latitude))
                )
              )) ASC
    """, nativeQuery = true)
    List<BranchEntity> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm
    );
}
