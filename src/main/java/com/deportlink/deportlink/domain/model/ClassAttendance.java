package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.ClassAttendanceStatus;

import java.util.Objects;

/**
 * Representa el estado de asistencia de un Player para una ClassSession concreta — ej: Pedro
 * pertenece al grupo fijo (ClassEnrollment activo) pero para la sesión del 10/09 canceló
 * (ClassAttendance = CANCELLED), sin que eso afecte su ClassEnrollment.
 * <p>
 * Transición de estado deliberadamente libre entre PENDING/CONFIRMED/CANCELLED, en cualquier
 * sentido — confirm()/cancel() son idempotentes, no lanzan si ya estaba en ese estado. Es una
 * decisión de diseño ya tomada (no una omisión): a diferencia de Reservation, acá no hay ventana
 * de cancelación ni cargo económico — es el registro manual de una conversación de WhatsApp que
 * puede cambiar de opinión varias veces antes de la clase. Ver
 * docs/class-management-mvp-design.md, sección 7, regla 8.
 * <p>
 * La restricción de que solo se puede confirmar/cancelar mientras la ClassSession esté SCHEDULED
 * requiere conocer el estado de OTRO agregado (ClassSession) — queda para el use case, no para
 * este record.
 */
public record ClassAttendance(Long id, Long classSessionId, Long playerId, ClassAttendanceStatus status) {

    public ClassAttendance {
        Objects.requireNonNull(classSessionId, "La clase (ClassSession) es obligatoria");
        Objects.requireNonNull(playerId, "El alumno es obligatorio");
        Objects.requireNonNull(status, "El estado es obligatorio");
    }

    /**
     * Crea la asistencia inicial de un alumno para una sesión — siempre nace PENDING. Se genera
     * una por cada ClassEnrollment activo del ClassSlot al momento de crear la ClassSession
     * (responsabilidad del use case, no de este factory).
     */
    public static ClassAttendance createPending(Long classSessionId, Long playerId) {
        return new ClassAttendance(null, classSessionId, playerId, ClassAttendanceStatus.PENDING);
    }

    public ClassAttendance confirm() {
        return withStatus(ClassAttendanceStatus.CONFIRMED);
    }

    public ClassAttendance cancel() {
        return withStatus(ClassAttendanceStatus.CANCELLED);
    }

    /**
     * Query anti-IDOR, mismo patrón que {@link Reservation#belongsTo} — no usada por ningún caso
     * de uso todavía (el MVP solo permite que el instructor confirme/cancele), pero deja el
     * dominio listo para cuando un Player confirme/cancele su propia asistencia sin necesitar
     * ningún cambio acá. Ver docs/class-management-mvp-design.md, sección 10.
     */
    public boolean belongsTo(Long aPlayerId) {
        return playerId.equals(aPlayerId);
    }

    public ClassAttendance withId(Long id) {
        return new ClassAttendance(id, classSessionId, playerId, status);
    }

    private ClassAttendance withStatus(ClassAttendanceStatus newStatus) {
        return new ClassAttendance(id, classSessionId, playerId, newStatus);
    }
}
