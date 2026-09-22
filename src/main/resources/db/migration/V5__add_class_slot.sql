-- ============================================================
-- V5__add_class_slot.sql
-- Plantilla/configuración recurrente de una clase — "todos los jueves a las 15:00".
-- active_status reutiliza el mismo enum ActiveStatus ya usado por club/branches/court (decisión
-- cerrada, ver docs/class-management-mvp-design.md). duration en minutos, vía DurationConverter
-- (ya existente, autoApply=true — mismo criterio que availability.slot_duration).
-- ============================================================

CREATE TABLE IF NOT EXISTS class_slot (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    instructor_id BIGINT       NOT NULL,
    court_id      BIGINT       NOT NULL,
    day_of_week   ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    start_time    TIME(6)      NOT NULL,
    duration      BIGINT       NOT NULL,
    level         ENUM('PRINCIPIANTE','INTERMEDIO','AVANZADO') NOT NULL,
    capacity      INT          NOT NULL,
    active_status ENUM('ACTIVE','INACTIVE') NOT NULL,
    PRIMARY KEY (id),
    KEY idx_class_slot_instructor (instructor_id),
    KEY idx_class_slot_court_day (court_id, day_of_week),
    CONSTRAINT fk_class_slot_instructor FOREIGN KEY (instructor_id) REFERENCES instructors (id),
    CONSTRAINT fk_class_slot_court FOREIGN KEY (court_id) REFERENCES court (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
