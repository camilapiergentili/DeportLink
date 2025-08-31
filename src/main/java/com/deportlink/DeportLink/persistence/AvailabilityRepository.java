package com.deportlink.DeportLink.persistence;

import com.deportlink.DeportLink.persistence.entity.AvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityRepository extends JpaRepository<AvailabilityEntity, Long> {
}
