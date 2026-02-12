package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.BranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {

    List<BranchEntity> findAllByClubId(long clubId);
}
