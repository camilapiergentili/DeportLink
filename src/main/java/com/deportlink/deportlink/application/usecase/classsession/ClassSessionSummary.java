package com.deportlink.deportlink.application.usecase.classsession;

import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.enums.Level;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Proyección de aplicación para el panel del instructor — ej: "Jueves 15:00, INTERMEDIO,
 * Cancha 2, 3/4". Compone datos de ClassSession, su ClassSlot y el conteo de ClassAttendance;
 * arma el resultado ya "listo para pantalla" para no forzar al futuro cliente HTTP a combinar
 * varias respuestas (mobile-first, ver docs/class-management-mvp-design.md, sección 13).
 * <p>
 * occupancy = PENDING + CONFIRMED (no solo confirmados: un PENDING todavía ocupa un lugar del
 * grupo hasta que se resuelva) — availableSpots es puramente informativo en este MVP, no habilita
 * inscripción de reemplazos.
 */
public record ClassSessionSummary(
        Long sessionId,
        Long classSlotId,
        LocalDate day,
        LocalTime startTime,
        Duration duration,
        ClassSessionStatus status,
        Long courtId,
        String courtName,
        Level level,
        int capacity,
        long pendingCount,
        long confirmedCount,
        long cancelledCount
) {
    public long occupancy() {
        return pendingCount + confirmedCount;
    }

    public int availableSpots() {
        return capacity - (int) occupancy();
    }
}
