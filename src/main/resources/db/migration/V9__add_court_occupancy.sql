-- ============================================================
-- V9__add_court_occupancy.sql
-- Garantía compartida "una Court no puede estar ocupada por una Reservation y una ClassSession al
-- mismo tiempo". source_type + source_id identifican el origen sin ambigüedad, para poder liberar
-- por origen (nunca por coordenadas) sin borrar la fila de otro.
--
-- IMPORTANTE — leer docs/class-management-stage-1c-persistence-design.md, secciones 8.2 y 8.3
-- antes de aplicar contra una base con datos reales:
--   * CREATE TABLE hace un commit implícito en MySQL/InnoDB — esta migración NO es una única
--     transacción atómica de DDL+DML. Si el INSERT de abajo fallara, la tabla queda creada de
--     todos modos.
--   * Antes de aplicar: verificar que uq_reservation_active_slot (V2) existe realmente, y correr
--     la consulta de detección de duplicados de coordenadas de reservas RESERVADO (sección 8.2)
--     — ninguna de las dos verificaciones se embebe en este script; son pasos manuales previos
--     del procedimiento de despliegue (sección 8.4), deliberadamente, para no automatizar una
--     decisión de abortar/continuar contra datos de producción.
--   * Después de aplicar: correr las consultas de correspondencia de cantidades y datos de la
--     sección 8.2 antes de dar la migración por buena.
--
-- El backfill usa el estado 'RESERVADO' de forma LITERAL — es el único valor de
-- StatusReservation con occupiesSlot()==true al día de esta migración. No se actualiza solo si
-- StatusReservation gana un valor ocupante nuevo en el futuro (ver sección 8.1).
-- ============================================================

CREATE TABLE IF NOT EXISTS court_occupancy (
    id            BIGINT  NOT NULL AUTO_INCREMENT,
    court_id      BIGINT  NOT NULL,
    occupied_day  DATE    NOT NULL,
    start_time    TIME(6) NOT NULL,
    source_type   ENUM('RESERVATION','CLASS_SESSION') NOT NULL,
    source_id     BIGINT  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_court_occupancy_slot (court_id, occupied_day, start_time),
    KEY idx_court_occupancy_source (source_type, source_id),
    CONSTRAINT fk_court_occupancy_court FOREIGN KEY (court_id) REFERENCES court (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO court_occupancy (court_id, occupied_day, start_time, source_type, source_id)
SELECT court_id, reservation_day, start_time, 'RESERVATION', id
FROM reservation
WHERE status = 'RESERVADO';
