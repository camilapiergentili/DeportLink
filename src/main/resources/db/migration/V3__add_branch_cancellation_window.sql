-- ============================================================
-- V3__add_branch_cancellation_window.sql
-- La ventana de cancelación de reservas (antes hardcodeada en 12 horas dentro de
-- Reservation.cancel()) pasa a ser configurable por sucursal — todas las canchas de una
-- Branch comparten el valor de su sucursal.
--
-- Backfill de datos: las sucursales que ya existen en la base no tienen este valor.
-- Se completa con 12 horas para TODAS las filas existentes — es una decisión de
-- MIGRACIÓN DE DATOS para preservar el comportamiento actual del sistema en producción,
-- no un default de aplicación. El código (Branch, BranchRequestDto) exige el valor de
-- forma explícita para toda sucursal nueva o actualizada — no hay ningún 12 hardcodeado
-- en Java a partir de esta migración.
--
-- Se agrega NULL primero, se backfillea, y recién después se agrega NOT NULL — evita que
-- el ALTER falle por filas existentes sin valor (MySQL exige un DEFAULT o una tabla vacía
-- para agregar NOT NULL directamente).
-- ============================================================

ALTER TABLE branches
    ADD COLUMN cancellation_window_hours INT NULL;

UPDATE branches
    SET cancellation_window_hours = 12
    WHERE cancellation_window_hours IS NULL;

ALTER TABLE branches
    MODIFY COLUMN cancellation_window_hours INT NOT NULL;
