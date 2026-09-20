package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.ClassEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollmentEntity, Long> {

    // Único (classSlotId, playerId) sin importar el estado active — permite reactivar en vez de duplicar.
    Optional<ClassEnrollmentEntity> findByClassSlot_IdAndPlayer_Id(Long classSlotId, Long playerId);

    List<ClassEnrollmentEntity> findByClassSlot_IdAndActiveTrue(Long classSlotId);

    long countByClassSlot_IdAndActiveTrue(Long classSlotId);
}
