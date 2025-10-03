package com.deportlink.DeportLink.persistence.repository;

import com.deportlink.DeportLink.model.entity.BranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {
}
