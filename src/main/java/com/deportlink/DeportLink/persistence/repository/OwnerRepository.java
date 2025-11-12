package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.OwnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OwnerRepository extends JpaRepository<OwnerEntity, Long> {

    Optional<OwnerEntity> findByDni(long dni);
    Optional<OwnerEntity> findByCuil(String cuil);
    Optional<OwnerEntity> findByEmail(String email);

}
