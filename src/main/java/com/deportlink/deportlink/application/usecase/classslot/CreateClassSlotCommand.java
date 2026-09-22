package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.enums.Level;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Comando de aplicación para CreateClassSlotUseCase — agrupa la configuración de la clase para
 * no tener un execute() de 7 parámetros posicionales. Cumple el rol que en otros módulos cumple
 * el *RequestDto (aún no existe capa HTTP para este módulo).
 */
public record CreateClassSlotCommand(
        Long instructorId,
        Long courtId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        Duration duration,
        Level level,
        int capacity
) {}
