package com.deportlink.DeportLink.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityResponseDto {

    private long id;
    private String day;
    private String openingTime;
    private String endingTime;

}
