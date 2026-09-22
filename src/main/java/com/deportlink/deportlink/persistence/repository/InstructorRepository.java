package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.InstructorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorRepository extends JpaRepository<InstructorEntity, Long> {
}
