package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.ClassEnrollment;

import java.util.List;
import java.util.Optional;

public interface ClassEnrollmentRepositoryPort {

    ClassEnrollment save(ClassEnrollment enrollment);

    /** Único (classSlotId, playerId) sin importar el estado active — permite reactivar en vez de duplicar. */
    Optional<ClassEnrollment> findByClassSlotIdAndPlayerId(Long classSlotId, Long playerId);

    /** Alumnos actualmente activos — usado por CreateClassSessionUseCase para generar asistencias. */
    List<ClassEnrollment> findActiveByClassSlotId(Long classSlotId);

    /**
     * Solo el conteo — AddPlayerToClassSlotUseCase necesita el número para ClassSlot.hasRoom(),
     * no la lista completa.
     */
    int countActiveByClassSlotId(Long classSlotId);
}
