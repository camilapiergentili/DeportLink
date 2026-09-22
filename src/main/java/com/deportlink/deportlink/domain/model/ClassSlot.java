package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.InvalidCapacityException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Aggregate Root del dominio de clases recurrentes de pádel.
 * <p>
 * ClassSlot = "todos los jueves a las 15:00, nivel intermedio, cancha 2, capacidad 4" — la
 * plantilla/configuración recurrente, sin fecha concreta. La ocurrencia de una fecha puntual es
 * {@link ClassSession}. Ver docs/class-management-mvp-design.md, secciones 5 y 6.
 * <p>
 * Inmutable por diseño (record), mismo criterio que {@link Reservation}/{@link Branch}: las
 * transiciones de estado devuelven una instancia nueva en vez de mutar.
 */
public record ClassSlot(
        Long id,
        Long instructorId,
        Long courtId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        Duration duration,
        Level level,
        int capacity,
        ActiveStatus status
) {

    /**
     * Capacidad máxima para el MVP — no es un límite técnico ni un valor de conveniencia: es una
     * decisión de negocio explícita (el profesor arma grupos de hasta 4 jugadores por WhatsApp,
     * ver docs/class-management-mvp-design.md). La capacidad sigue siendo configurable por cada
     * ClassSlot (1..4) — este máximo acota el rango, no reemplaza la configurabilidad. Si el
     * negocio permite grupos más grandes en el futuro, este valor es el único lugar a revisar.
     */
    public static final int MAX_CAPACITY = 4;

    public ClassSlot {
        Objects.requireNonNull(instructorId, "El instructor es obligatorio");
        Objects.requireNonNull(courtId, "La cancha es obligatoria");
        Objects.requireNonNull(dayOfWeek, "El día de la semana es obligatorio");
        Objects.requireNonNull(startTime, "El horario de inicio es obligatorio");
        Objects.requireNonNull(duration, "La duración es obligatoria");
        Objects.requireNonNull(level, "El nivel es obligatorio");
        Objects.requireNonNull(status, "El estado es obligatorio");

        if (duration.isNegative() || duration.isZero()) {
            throw new InvalidTimeRangeException("La duración de la clase debe ser positiva");
        }
        if (capacity <= 0) {
            throw new InvalidCapacityException("La capacidad debe ser mayor a cero");
        }
        if (capacity > MAX_CAPACITY) {
            throw new InvalidCapacityException(
                    "La capacidad no puede superar el máximo de " + MAX_CAPACITY + " alumnos");
        }
    }

    /**
     * Crea un ClassSlot nuevo. Nace siempre ACTIVE — un instructor lo pausa explícitamente
     * después si hace falta (pause()), igual que Court/Branch nacen en su estado inicial propio.
     */
    public static ClassSlot create(Long instructorId, Long courtId, DayOfWeek dayOfWeek,
                                    LocalTime startTime, Duration duration, Level level, int capacity) {
        return new ClassSlot(null, instructorId, courtId, dayOfWeek, startTime, duration,
                level, capacity, ActiveStatus.ACTIVE);
    }

    /**
     * Pausa la generación de nuevas ClassSession para este ClassSlot. No afecta el
     * ClassEnrollment (el grupo fijo) ni las ClassSession ya creadas — ver
     * docs/class-management-mvp-design.md, caso de uso PauseClassSlotUseCase.
     */
    public ClassSlot pause() {
        if (!isActive()) throw new StatusAlreadyAppliedException("El horario ya está pausado");
        return withStatus(ActiveStatus.INACTIVE);
    }

    public ClassSlot reactivate() {
        if (isActive()) throw new StatusAlreadyAppliedException("El horario ya está activo");
        return withStatus(ActiveStatus.ACTIVE);
    }

    public boolean isActive() {
        return status == ActiveStatus.ACTIVE;
    }

    /**
     * Regla de capacidad que SÍ puede resolverse dentro del dominio: dado el conteo de
     * ClassEnrollment activos (resuelto por el use case contra el repositorio), decide si hay
     * lugar. La garantía contra dos altas concurrentes ocupando el último lugar (lock pesimista)
     * es responsabilidad de la capa de aplicación/infraestructura — no de esta etapa. Ver
     * docs/class-management-mvp-design.md, sección 8.
     */
    public boolean hasRoom(int activeEnrollmentCount) {
        return activeEnrollmentCount < capacity;
    }

    public ClassSlot withId(Long id) {
        return new ClassSlot(id, instructorId, courtId, dayOfWeek, startTime, duration, level, capacity, status);
    }

    private ClassSlot withStatus(ActiveStatus newStatus) {
        return new ClassSlot(id, instructorId, courtId, dayOfWeek, startTime, duration, level, capacity, newStatus);
    }
}
