package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityRequestDto {

    private String day;
    private String openingDay;
    private String closingTime;
    private long idCourt;
}
