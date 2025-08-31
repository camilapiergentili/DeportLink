package com.deportlink.DeportLink.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Court {

    private long id;
    private String name;
    private double pricePerHour;

    private Address address;
    private Sport sport;
    private User owner;
    private Set<Reservation> reservations;
    private Set<Availability> availabilities;

}
