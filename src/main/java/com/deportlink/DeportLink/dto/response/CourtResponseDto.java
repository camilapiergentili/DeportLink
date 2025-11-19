package com.deportlink.DeportLink.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CourtResponseDto {

    private long id;
    private String name;
    private double pricePerHour;
    private String sport;
    private Set<ScheduleResponseDto> availabilities;

}
