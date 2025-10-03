package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationRequestDto {

    private String day;
    private String startTime;
    private Long durationMinutes;
    private long idCourt;
    private long idPlayer;

}
