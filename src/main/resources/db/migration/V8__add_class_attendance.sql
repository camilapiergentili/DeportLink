-- ============================================================
-- V8__add_class_attendance.sql
-- Estado de asistencia de un Player para una ClassSession concreta — historial independiente del
-- ClassEnrollment (cancelar una asistencia puntual no modifica la pertenencia al grupo fijo).
-- ============================================================

CREATE TABLE IF NOT EXISTS class_attendance (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    class_session_id  BIGINT NOT NULL,
    player_id         BIGINT NOT NULL,
    status            ENUM('PENDING','CONFIRMED','CANCELLED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_attendance_session_player (class_session_id, player_id),
    KEY idx_class_attendance_player (player_id),
    CONSTRAINT fk_class_attendance_session FOREIGN KEY (class_session_id) REFERENCES class_session (id),
    CONSTRAINT fk_class_attendance_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
