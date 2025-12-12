package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleRequestDto {

    private String day;
    private String openingTime;
    private String closingTime;
}
