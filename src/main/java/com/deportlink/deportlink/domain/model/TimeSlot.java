package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.exception.InvalidTimeRangeException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Value Object que representa un turno específico en el tiempo.
 * Inmutable por diseño: dos TimeSlots son iguales si día, hora y duración coinciden.
 * Encapsula las preguntas "¿es futuro?" y "¿cuánto dura en horas?"
 * para que no queden dispersas por los servicios.
 */
public record TimeSlot(LocalDate day, LocalTime startTime, Duration duration) {

    public TimeSlot {
        Objects.requireNonNull(day, "El día no puede ser nulo");
        Objects.requireNonNull(startTime, "El horario de inicio no puede ser nulo");
        Objects.requireNonNull(duration, "La duración no puede ser nula");
        if (duration.isNegative() || duration.isZero()) {
            throw new InvalidTimeRangeException("La duración del turno debe ser positiva");
        }
    }

    public LocalDateTime toDateTime() {
        return LocalDateTime.of(day, startTime);
    }

    public boolean isFuture() {
        return !toDateTime().isBefore(LocalDateTime.now());
    }

    public double durationInHours() {
        return duration.toMinutes() / 60.0;
    }

    public boolean isSameSlotAs(LocalDate otherDay, LocalTime otherTime) {
        return day.equals(otherDay) && startTime.equals(otherTime);
    }
}