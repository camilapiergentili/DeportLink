package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Representa la ocurrencia concreta de una clase en una fecha determinada — ej: si el ClassSlot
 * es "todos los jueves a las 15:00", una ClassSession es "jueves 10/09/2026 a las 15:00".
 * <p>
 * day/startTime/duration se copian del ClassSlot al momento de crear la sesión (snapshot), no se
 * leen dinámicamente de él — mismo criterio que {@link Ticket} congela precio/nombre de cancha al
 * momento de emitirse: si el instructor edita el ClassSlot después, las sesiones ya creadas no
 * deben cambiar. Ver docs/class-management-mvp-design.md, sección 5.
 * <p>
 * La generación automática de sesiones desde un ClassSlot recurrente (job/@Scheduled) queda
 * explícitamente fuera de esta etapa — la creación es manual, vía {@link #create}.
 */
public record ClassSession(
        Long id,
        Long classSlotId,
        LocalDate day,
        LocalTime startTime,
        Duration duration,
        ClassSessionStatus status
) {

    public ClassSession {
        Objects.requireNonNull(classSlotId, "El horario (ClassSlot) es obligatorio");
        Objects.requireNonNull(day, "La fecha es obligatoria");
        Objects.requireNonNull(startTime, "El horario de inicio es obligatorio");
        Objects.requireNonNull(duration, "La duración es obligatoria");
        Objects.requireNonNull(status, "El estado es obligatorio");

        if (duration.isNegative() || duration.isZero()) {
            throw new InvalidTimeRangeException("La duración de la clase debe ser positiva");
        }
    }

    /**
     * Crea la ocurrencia concreta de una fecha para un ClassSlot. El dominio garantiza que la
     * fecha/hora sea futura — mismo criterio que {@link Reservation#create}. La validación de que
     * el ClassSlot esté activo, y de que el día coincida con su dayOfWeek, requiere leer el
     * estado de OTRO agregado (ClassSlot) — queda para el use case (CreateClassSessionUseCase,
     * Etapa 1B), no para este constructor. Ver docs/class-management-mvp-design.md, sección 11.
     */
    public static ClassSession create(Long classSlotId, LocalDate day, LocalTime startTime, Duration duration) {
        LocalDateTime sessionStart = LocalDateTime.of(day, startTime);
        if (sessionStart.isBefore(LocalDateTime.now())) {
            throw new InvalidTimeRangeException("No se puede crear una clase en una fecha/hora que ya pasó");
        }
        return new ClassSession(null, classSlotId, day, startTime, duration, ClassSessionStatus.SCHEDULED);
    }

    public boolean isScheduled() {
        return status == ClassSessionStatus.SCHEDULED;
    }

    public ClassSession withId(Long id) {
        return new ClassSession(id, classSlotId, day, startTime, duration, status);
    }
}
