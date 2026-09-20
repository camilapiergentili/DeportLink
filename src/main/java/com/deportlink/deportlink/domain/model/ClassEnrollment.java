package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;

import java.util.Objects;

/**
 * Representa que un Player pertenece al grupo fijo de un ClassSlot recurrente — ej: "Pedro
 * pertenece al grupo fijo de los jueves a las 15:00".
 * <p>
 * NO representa la asistencia a una fecha puntual — eso es {@link ClassAttendance}. Un
 * ClassEnrollment activo puede coexistir con una ClassAttendance CANCELLED para una semana
 * concreta, sin contradicción: son preguntas distintas ("¿es del grupo, en general?" vs.
 * "¿viene esta semana?"). Ver docs/class-management-mvp-design.md, sección 6.
 */
public record ClassEnrollment(Long id, Long classSlotId, Long playerId, boolean active) {

    public ClassEnrollment {
        Objects.requireNonNull(classSlotId, "El horario (ClassSlot) es obligatorio");
        Objects.requireNonNull(playerId, "El alumno es obligatorio");
    }

    /**
     * Suma un alumno al grupo fijo. Nace siempre activo — la regla de "no superar la capacidad
     * del ClassSlot" NO vive acá: para evaluarla hace falta conocer cuántos otros enrollments
     * ya existen para el mismo ClassSlot, algo que este record no tiene forma de saber por sí
     * solo. Esa regla se resuelve en el use case (AddPlayerToClassSlotUseCase, Etapa 1B) contra
     * ClassSlot.hasRoom(conteo), no aquí. Ver docs/class-management-mvp-design.md, sección 8.
     */
    public static ClassEnrollment create(Long classSlotId, Long playerId) {
        return new ClassEnrollment(null, classSlotId, playerId, true);
    }

    /** Baja lógica del grupo fijo — nunca se elimina la fila (ver PlayerEntity, comentario sobre Reservation como historial). */
    public ClassEnrollment deactivate() {
        if (!active) throw new StatusAlreadyAppliedException("El alumno ya está inactivo en este horario");
        return new ClassEnrollment(id, classSlotId, playerId, false);
    }

    /** Reincorpora al grupo fijo a un alumno dado de baja previamente, sin duplicar la fila. */
    public ClassEnrollment activate() {
        if (active) throw new StatusAlreadyAppliedException("El alumno ya está activo en este horario");
        return new ClassEnrollment(id, classSlotId, playerId, true);
    }

    public ClassEnrollment withId(Long id) {
        return new ClassEnrollment(id, classSlotId, playerId, active);
    }
}
