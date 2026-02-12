package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    Optional<PlayerEntity> findByEmail(String email);
}
