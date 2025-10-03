package com.deportlink.DeportLink.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationResponseDto {

    private long id;
    private String day;
    private String startTime;
    private Long duration;

    private String nameCourt;
    private String sport;
    private String namePlayer;
    private double totalPrice;
    private String address;


}
