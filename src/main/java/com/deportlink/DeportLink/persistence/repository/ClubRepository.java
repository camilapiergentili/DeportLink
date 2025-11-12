package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<ClubEntity, Long> {

    Optional<ClubEntity> findByCuit(String cuit);
    Optional<ClubEntity> findByLegalName(String legalName);

}
