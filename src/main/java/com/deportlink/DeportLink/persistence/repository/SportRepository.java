package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.SportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SportRepository extends JpaRepository<SportEntity, Long> {

    Optional<SportEntity> findByNameSport(String nameSport);
}
