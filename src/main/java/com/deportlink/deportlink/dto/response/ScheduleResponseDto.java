package com.deportlink.deportlink.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
public class ScheduleResponseDto {

    private long id;
    private DayOfWeek day;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private long slotDuration;
}
