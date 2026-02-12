package com.deportlink.deportlink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationRequestDto {

    private String day;
    private String startTime;
    private long idCourt;
    private long idPlayer;

}
