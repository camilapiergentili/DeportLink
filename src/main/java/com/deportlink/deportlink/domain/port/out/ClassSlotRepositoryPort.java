package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.ClassSlot;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port de salida del dominio ClassSlot — mismo criterio que ReservationRepositoryPort/
 * CourtRepositoryPort: define QUÉ necesita el dominio/aplicación de la persistencia, sin saber
 * CÓMO está implementado. Sin adapter todavía (ver docs/class-management-mvp-design.md).
 */
public interface ClassSlotRepositoryPort {
    /** First locking read for attendance edits, serializing them with group changes. */
    Optional<ClassSlot> findByAttendanceIdForUpdate(Long attendanceId);

    ClassSlot save(ClassSlot classSlot);

    Optional<ClassSlot> findById(Long id);

    /**
     * Lock pesimista sobre el ClassSlot — primera (o segunda, detrás del lock de Court en
     * CreateClassSessionUseCase) lectura de la transacción en todo caso de uso que mute el
     * ClassSlot o su grupo (pause/reactivate/addPlayer/removePlayer/createSession). Mismo
     * mecanismo que CourtRepositoryPort.findByIdForUpdate: serializa las mutaciones concurrentes
     * sobre el mismo ClassSlot.
     */
    Optional<ClassSlot> findByIdForUpdate(Long id);

    List<ClassSlot> findAllByInstructorId(Long instructorId);

    /** Búsqueda batch — evita una consulta por sesión al enriquecer un listado de agenda. */
    List<ClassSlot> findAllByIds(Set<Long> ids);
}
