package com.deportlink.deportlink.enums;

/**
 * Estado de la ocurrencia concreta de una clase (ClassSession) — distinto del estado de
 * asistencia de cada alumno (ClassAttendanceStatus). Toda ClassSession nace SCHEDULED; ningún
 * caso de uso de esta etapa cambia este estado (no hay "cancelar clase completa" todavía — ver
 * docs/class-management-mvp-design.md, secciones 3 y 11). El valor CANCELLED se deja modelado
 * a propósito para no requerir otra migración/cambio de dominio cuando ese caso de uso se agregue.
 */
public enum ClassSessionStatus {
    SCHEDULED,
    CANCELLED
}
