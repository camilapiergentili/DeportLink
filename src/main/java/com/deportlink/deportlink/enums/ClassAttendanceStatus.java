package com.deportlink.deportlink.enums;

/**
 * Estado de asistencia de un Player para una ClassSession concreta — NO es el estado del
 * ClassEnrollment (pertenencia al grupo fijo) ni el de la ClassSession misma (ClassSessionStatus).
 * Deliberadamente no se llama "Status" a secas: seguimos la convención ya establecida en el
 * proyecto de que cada enum de estado lleva el nombre de lo que gobierna
 * (StatusReservation, VerificationStatus, ActiveStatus) — ver
 * docs/class-management-mvp-design.md, sección 5.
 */
public enum ClassAttendanceStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
