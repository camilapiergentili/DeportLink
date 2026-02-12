package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.SportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SportRepository extends JpaRepository<SportEntity, Long> {

    Optional<SportEntity> findByNameSport(String nameSport);
}
