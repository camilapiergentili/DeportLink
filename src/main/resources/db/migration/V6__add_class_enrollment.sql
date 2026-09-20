-- ============================================================
-- V6__add_class_enrollment.sql
-- Pertenencia de un Player al grupo fijo de un ClassSlot. UNIQUE(class_slot_id, player_id) SIN
-- filtrar por active a propósito: un alumno tiene una única fila por horario, esté activa o no —
-- AddPlayerToClassSlotUseCase (1B) reactiva la fila existente en vez de insertar una segunda.
-- ============================================================

CREATE TABLE IF NOT EXISTS class_enrollment (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    class_slot_id BIGINT NOT NULL,
    player_id     BIGINT NOT NULL,
    active        BIT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_enrollment_slot_player (class_slot_id, player_id),
    KEY idx_class_enrollment_player (player_id),
    CONSTRAINT fk_class_enrollment_slot FOREIGN KEY (class_slot_id) REFERENCES class_slot (id),
    CONSTRAINT fk_class_enrollment_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
