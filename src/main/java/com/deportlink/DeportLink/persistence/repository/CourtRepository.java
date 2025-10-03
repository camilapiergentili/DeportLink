package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.CourtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository extends JpaRepository<CourtEntity, Long> {
}
