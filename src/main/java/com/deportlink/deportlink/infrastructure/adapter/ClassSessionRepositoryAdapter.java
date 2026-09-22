package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.model.entity.ClassSessionEntity;
import com.deportlink.deportlink.persistence.repository.ClassSessionRepository;
import com.deportlink.deportlink.persistence.repository.ClassSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ClassSession NO persiste courtId — decisión cerrada, ver
 * docs/class-management-stage-1c-persistence-design.md, sección 3.4. toDomain() nunca lee
 * entity.getClassSlot().getCourt() para construir el record; la cancha se resuelve siempre a
 * través de ClassSlot en la capa de aplicación.
 */
@Component
@RequiredArgsConstructor
public class ClassSessionRepositoryAdapter implements ClassSessionRepositoryPort {

    private final ClassSessionRepository classSessionRepository;
    private final ClassSlotRepository classSlotRepository;

    @Override
    public List<ClassSession> findScheduledAfter(Long slotId, java.time.LocalDateTime after) {
        return classSessionRepository.findScheduledAfter(slotId, after.toLocalDate(), after.toLocalTime())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public ClassSession save(ClassSession session) {
        // ClassSession no se edita post-creación (sin caso de uso que lo haga) — siempre alta nueva.
        ClassSessionEntity entity = new ClassSessionEntity();
        entity.setClassSlot(classSlotRepository.getReferenceById(session.classSlotId()));
        entity.setSessionDate(session.day());
        entity.setStartTime(session.startTime());
        entity.setDuration(session.duration());
        entity.setStatus(session.status());
        return toDomain(classSessionRepository.save(entity));
    }

    @Override
    public Optional<ClassSession> findById(Long id) {
        return classSessionRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByClassSlotIdAndDay(Long classSlotId, LocalDate day) {
        return classSessionRepository.existsByClassSlot_IdAndSessionDate(classSlotId, day);
    }

    @Override
    public List<ClassSession> findByInstructor(Long instructorId, LocalDate fromInclusive, LocalDate toInclusive) {
        return classSessionRepository.findByInstructorAndDateRange(instructorId, fromInclusive, toInclusive)
                .stream().map(this::toDomain).toList();
    }

    private ClassSession toDomain(ClassSessionEntity entity) {
        return new ClassSession(
                entity.getId(),
                entity.getClassSlot().getId(),
                entity.getSessionDate(),
                entity.getStartTime(),
                entity.getDuration(),
                entity.getStatus()
        );
    }
}
