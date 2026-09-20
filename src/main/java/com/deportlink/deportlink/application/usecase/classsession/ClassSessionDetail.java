package com.deportlink.deportlink.application.usecase.classsession;

import java.util.List;

/**
 * Detalle completo de una ClassSession = los mismos datos del resumen de agenda + el roster de
 * alumnos con su estado — evita duplicar los ~10 campos de ClassSessionSummary en un record
 * aparte (ver spec de esta etapa, sección 9: "Devolver los datos del resumen y alumnos con...").
 */
public record ClassSessionDetail(ClassSessionSummary summary, List<ClassAttendanceView> attendees) {}
