package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.model.entity.ClassSlotEntity;
import com.deportlink.deportlink.persistence.repository.ClassSlotRepository;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.persistence.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter de infraestructura para ClassSlotRepositoryPort — mismo patrón que
 * ReservationRepositoryAdapter: mapeo manual dominio↔entidad, id==null decide insert vs update.
 */
@Component
@RequiredArgsConstructor
public class ClassSlotRepositoryAdapter implements ClassSlotRepositoryPort {

    private final ClassSlotRepository classSlotRepository;
    private final InstructorRepository instructorRepository;
    private final CourtRepository courtRepository;

    @Override
    public Optional<ClassSlot> findByAttendanceIdForUpdate(Long attendanceId) {
        return classSlotRepository.findByAttendanceIdForUpdate(attendanceId).map(this::toDomain);
    }

    @Override
    public ClassSlot save(ClassSlot classSlot) {
        ClassSlotEntity entity = classSlot.id() == null
                ? buildNewEntity(classSlot)
                : updateExistingEntity(classSlot);
        return toDomain(classSlotRepository.save(entity));
    }

    @Override
    public Optional<ClassSlot> findById(Long id) {
        return classSlotRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ClassSlot> findByIdForUpdate(Long id) {
        return classSlotRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public List<ClassSlot> findAllByInstructorId(Long instructorId) {
        return classSlotRepository.findAllByInstructor_Id(instructorId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ClassSlot> findAllByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return classSlotRepository.findAllByIdIn(ids).stream().map(this::toDomain).toList();
    }

    private ClassSlotEntity buildNewEntity(ClassSlot domain) {
        ClassSlotEntity entity = new ClassSlotEntity();
        entity.setInstructor(instructorRepository.getReferenceById(domain.instructorId()));
        entity.setCourt(courtRepository.getReferenceById(domain.courtId()));
        applyFields(entity, domain);
        return entity;
    }

    private ClassSlotEntity updateExistingEntity(ClassSlot domain) {
        ClassSlotEntity entity = classSlotRepository.findById(domain.id())
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));
        // instructor/court no cambian post-creación (no hay caso de uso de edición) — solo los
        // campos que las transiciones de dominio (pause/reactivate) realmente mutan.
        applyFields(entity, domain);
        return entity;
    }

    private void applyFields(ClassSlotEntity entity, ClassSlot domain) {
        entity.setDayOfWeek(domain.dayOfWeek());
        entity.setStartTime(domain.startTime());
        entity.setDuration(domain.duration());
        entity.setLevel(domain.level());
        entity.setCapacity(domain.capacity());
        entity.setActiveStatus(domain.status());
    }

    private ClassSlot toDomain(ClassSlotEntity entity) {
        return new ClassSlot(
                entity.getId(),
                entity.getInstructor().getId(),
                entity.getCourt().getId(),
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getDuration(),
                entity.getLevel(),
                entity.getCapacity(),
                entity.getActiveStatus()
        );
    }
}
