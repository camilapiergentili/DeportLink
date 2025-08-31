package com.deportlink.DeportLink.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Availability {

    private long id;
    private DayOfWeek day;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private Court court;

}
