package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.ClassSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepositoryPort {
    List<ClassSession> findScheduledAfter(Long classSlotId, java.time.LocalDateTime after);

    ClassSession save(ClassSession session);

    Optional<ClassSession> findById(Long id);

    boolean existsByClassSlotIdAndDay(Long classSlotId, LocalDate day);

    /**
     * Sesiones de los ClassSlot de un instructor, en el rango [fromInclusive, toInclusive].
     * {@code toInclusive} en {@code null} significa sin límite superior — ver
     * GetClassSessionsByInstructorUseCase. El join instructor→classSlot→session vive del lado
     * del adapter (mismo criterio que CourtRepository.existsByCourtAndOwner), no acá.
     */
    List<ClassSession> findByInstructor(Long instructorId, LocalDate fromInclusive, LocalDate toInclusive);
}
