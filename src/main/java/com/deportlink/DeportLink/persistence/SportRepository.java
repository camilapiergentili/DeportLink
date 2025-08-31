package com.deportlink.DeportLink.persistence;

import com.deportlink.DeportLink.persistence.entity.SportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SportRepository extends JpaRepository<SportEntity, Long> {
}
