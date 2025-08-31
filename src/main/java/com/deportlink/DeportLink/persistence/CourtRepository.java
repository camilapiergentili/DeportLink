package com.deportlink.DeportLink.persistence;

import com.deportlink.DeportLink.persistence.entity.CourtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository extends JpaRepository<CourtEntity, Long> {
}
