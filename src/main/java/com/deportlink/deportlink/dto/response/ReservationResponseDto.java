package com.deportlink.deportlink.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationResponseDto {

    private long id;
    private String day;
    private String startTime;
    private Long duration;
    private TicketResponseDto ticket;

}
