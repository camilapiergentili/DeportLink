package com.deportlink.deportlink.dto.response;

import com.deportlink.deportlink.enums.StatusReservation;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationResponseDto {

    private Long id;
    private LocalDate day;
    private LocalTime startTime;
    private long durationMinutes;
    private StatusReservation status;
    private TicketResponseDto ticket;

}
