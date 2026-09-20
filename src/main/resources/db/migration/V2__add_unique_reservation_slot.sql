-- ============================================================
-- V2__add_unique_reservation_slot.sql
-- Defensa en profundidad para la garantía central "no doble reserva": un índice único a
-- nivel de base de datos, además del lock pesimista de aplicación (BookReservationUseCase /
-- RescheduleReservationUseCase).
--
-- MySQL no soporta índices únicos parciales/filtrados de forma nativa (no hay
-- CREATE UNIQUE INDEX ... WHERE como en Postgres). Workaround estándar: una columna generada
-- que es NULL salvo que status = 'RESERVADO', y el UNIQUE sobre esa columna — InnoDB excluye
-- de la verificación de unicidad cualquier fila con un NULL en el índice compuesto, así que
-- las reservas CANCELADO/FINALIZADO/REPROGRAMADO nunca chocan entre sí ni con una activa,
-- pero dos filas RESERVADO para el mismo court_id/reservation_day/start_time sí violan el
-- índice.
--
-- Verificado contra la base real antes de esta migración: 0 filas en reservation, sin
-- conflictos posibles.
-- ============================================================

ALTER TABLE reservation
    ADD COLUMN active_slot_court_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status = 'RESERVADO' THEN court_id ELSE NULL END) STORED;

ALTER TABLE reservation
    ADD UNIQUE KEY uq_reservation_active_slot (active_slot_court_id, reservation_day, start_time);
