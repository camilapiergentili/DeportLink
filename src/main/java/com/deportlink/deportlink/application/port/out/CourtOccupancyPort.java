package com.deportlink.deportlink.application.port.out;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Puerto de la garantía "una Court no puede estar ocupada por una Reservation y una ClassSession
 * al mismo tiempo" — ver docs/class-management-stage-1c-persistence-design.md, secciones 3.6 y 6.
 * <p>
 * Implementado por {@code CourtOccupancyAdapter} (tabla {@code court_occupancy}), e integrado en
 * ambos sentidos: {@code CreateClassSessionUseCase} (1B, sin cambios) y
 * {@code BookReservationUseCase}/{@code CancelReservationUseCase}/{@code RescheduleReservationUseCase}
 * (Etapa 1C).
 * <p>
 * La detección de conflicto es por coincidencia EXACTA de (courtId, día, horario de inicio) — no
 * detecta solapamiento general de intervalos de duración distinta. Es la misma limitación ya
 * aceptada por el mecanismo de unicidad de Reservation (V2), no una nueva.
 * <p>
 * {@code releaseForClassSession} NO existe — cancelar una ClassSession completa sigue fuera de
 * alcance del MVP (ver diseño de la Etapa 1C, decisión cerrada).
 */
public interface CourtOccupancyPort {

    boolean existsOccupancy(Long courtId, LocalDate day, LocalTime startTime);

    /** classSessionId ya debe existir (se llama después de guardar la ClassSession). */
    void registerForClassSession(Long classSessionId, Long courtId, LocalDate day, LocalTime startTime);

    /** reservationId ya debe existir (se llama después de guardar la Reservation). */
    void registerForReservation(Long reservationId, Long courtId, LocalDate day, LocalTime startTime);

    /** Libera por origen (source_type=RESERVATION, source_id=reservationId) — nunca por coordenadas. */
    void releaseForReservation(Long reservationId);
}
