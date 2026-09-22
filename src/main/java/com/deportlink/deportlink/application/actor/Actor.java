package com.deportlink.deportlink.application.actor;

import java.util.Objects;

/**
 * Contexto mínimo de quién ejecuta un caso de uso del módulo de clases. Deliberadamente NO
 * importa {@code Authentication}, {@code UserEntity} ni {@code SecurityContext} — esos conceptos
 * son de infraestructura/seguridad y quedan para una etapa futura, cuando un
 * {@code HandlerMethodArgumentResolver} (mismo patrón que {@code CurrentUserId}) construya un
 * {@code Actor} confiable a partir del JWT.
 * <p>
 * Esta clase asume que {@code id} y {@code role} ya vienen resueltos y validados por una fuente
 * confiable (la autenticación) — nunca debe construirse a partir de datos del body HTTP como
 * prueba de identidad o permisos.
 * <p>
 * Centraliza la única política de ownership real del módulo ({@link #canAccess}) para no
 * repetir "es ADMIN, o es el instructor dueño del recurso" en cada caso de uso — no es un
 * framework de permisos, es un value object con dos métodos de consulta, mismo espíritu que
 * {@code Reservation.belongsTo}.
 */
public record Actor(Long id, ActorRole role) {

    public Actor {
        Objects.requireNonNull(id, "El id del actor es obligatorio");
        Objects.requireNonNull(role, "El rol del actor es obligatorio");
    }

    public boolean isAdmin() {
        return role == ActorRole.ADMIN;
    }

    public boolean isInstructor() {
        return role == ActorRole.INSTRUCTOR;
    }

    public boolean owns(Long resourceInstructorId) {
        return id.equals(resourceInstructorId);
    }

    /**
     * True si este actor puede operar sobre un recurso perteneciente a {@code resourceInstructorId}
     * — ADMIN siempre puede; un INSTRUCTOR solo sobre lo suyo. Los casos de uso traducen "false"
     * a la excepción de "recurso no encontrado" correspondiente (nunca 403 explícito) — mismo
     * criterio anti-IDOR que {@code CancelReservationUseCase}.
     */
    public boolean canAccess(Long resourceInstructorId) {
        return isAdmin() || owns(resourceInstructorId);
    }
}
