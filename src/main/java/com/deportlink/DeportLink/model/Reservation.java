package com.deportlink.DeportLink.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Reservation {

    private long id;
    private LocalDate day;
    private LocalTime startTime;
    private LocalTime endTime;
    private Duration duration;
    private Court court;
    private User player;
    private StatusReservation status;

    public Duration getDuration(){
        return Duration.between(startTime, endTime);
    }

}
