-- ============================================================
-- V4__add_instructor_role_and_table.sql
-- Agrega el rol INSTRUCTOR y la tabla instructors — subtipo de users, mismo patrón exacto que
-- owners/players (JOINED inheritance, sin columnas propias, ver InstructorEntity).
--
-- La modificación de `users.role` es la única migración de todo este módulo que toca una
-- columna con datos reales en producción — verificar contra un dump real antes de aplicar (ver
-- docs/class-management-stage-1c-persistence-design.md, criterio de aceptación 10.6.7).
-- Se preserva la nulabilidad original de la columna (V1: sin NOT NULL) — agregar un valor a un
-- ENUM es aditivo y no requiere backfill: ninguna fila existente tenía ni podía tener 'INSTRUCTOR'.
-- ============================================================

ALTER TABLE users
    MODIFY COLUMN role ENUM('ADMIN','OWNER','PLAYER','INSTRUCTOR');

CREATE TABLE IF NOT EXISTS instructors (
    id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_instructors_users FOREIGN KEY (id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
