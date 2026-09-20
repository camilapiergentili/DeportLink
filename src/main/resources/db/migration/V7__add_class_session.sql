-- ============================================================
-- V7__add_class_session.sql
-- Ocurrencia concreta de una fecha para un ClassSlot. Deliberadamente SIN court_id — decisión
-- cerrada, ver docs/class-management-stage-1c-persistence-design.md, sección 3.4: la cancha se
-- resuelve siempre a través de class_slot; la disponibilidad compartida se consulta contra
-- court_occupancy (V9), que ya tiene su propio court_id. No duplicar esa referencia acá.
-- ============================================================

CREATE TABLE IF NOT EXISTS class_session (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    class_slot_id BIGINT   NOT NULL,
    session_date  DATE     NOT NULL,
    start_time    TIME(6)  NOT NULL,
    duration      BIGINT   NOT NULL,
    status        ENUM('SCHEDULED','CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_session_slot_date (class_slot_id, session_date),
    CONSTRAINT fk_class_session_slot FOREIGN KEY (class_slot_id) REFERENCES class_slot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
