package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import com.deportlink.deportlink.model.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
}
