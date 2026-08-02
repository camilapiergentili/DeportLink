package com.deportlink.deportlink.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

public record Schedule(
        Long id,
        Long courtId,
        DayOfWeek day,
        LocalTime openingTime,
        LocalTime closingTime,
        Duration slotDuration
) {}