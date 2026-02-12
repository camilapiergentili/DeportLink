package com.deportlink.deportlink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleRequestDto {

    private String day;
    private String openingTime;
    private String closingTime;
    private Long slotDuration;
}
