package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.exception.ClassEnrollmentNotFoundException;
import com.deportlink.deportlink.model.entity.ClassEnrollmentEntity;
import com.deportlink.deportlink.persistence.repository.ClassEnrollmentRepository;
import com.deportlink.deportlink.persistence.repository.ClassSlotRepository;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClassEnrollmentRepositoryAdapter implements ClassEnrollmentRepositoryPort {

    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassSlotRepository classSlotRepository;
    private final PlayerRepository playerRepository;

    @Override
    public ClassEnrollment save(ClassEnrollment enrollment) {
        ClassEnrollmentEntity entity = enrollment.id() == null
                ? buildNewEntity(enrollment)
                : updateExistingEntity(enrollment);
        return toDomain(classEnrollmentRepository.save(entity));
    }

    @Override
    public Optional<ClassEnrollment> findByClassSlotIdAndPlayerId(Long classSlotId, Long playerId) {
        return classEnrollmentRepository.findByClassSlot_IdAndPlayer_Id(classSlotId, playerId).map(this::toDomain);
    }

    @Override
    public List<ClassEnrollment> findActiveByClassSlotId(Long classSlotId) {
        return classEnrollmentRepository.findByClassSlot_IdAndActiveTrue(classSlotId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public int countActiveByClassSlotId(Long classSlotId) {
        return (int) classEnrollmentRepository.countByClassSlot_IdAndActiveTrue(classSlotId);
    }

    private ClassEnrollmentEntity buildNewEntity(ClassEnrollment domain) {
        ClassEnrollmentEntity entity = new ClassEnrollmentEntity();
        entity.setClassSlot(classSlotRepository.getReferenceById(domain.classSlotId()));
        entity.setPlayer(playerRepository.getReferenceById(domain.playerId()));
        entity.setActive(domain.active());
        return entity;
    }

    private ClassEnrollmentEntity updateExistingEntity(ClassEnrollment domain) {
        ClassEnrollmentEntity entity = classEnrollmentRepository.findById(domain.id())
                .orElseThrow(() -> new ClassEnrollmentNotFoundException("No se encontró la inscripción"));
        // classSlot/player no cambian post-creación — solo active (activate()/deactivate()).
        entity.setActive(domain.active());
        return entity;
    }

    private ClassEnrollment toDomain(ClassEnrollmentEntity entity) {
        return new ClassEnrollment(
                entity.getId(),
                entity.getClassSlot().getId(),
                entity.getPlayer().getId(),
                entity.isActive()
        );
    }
}
