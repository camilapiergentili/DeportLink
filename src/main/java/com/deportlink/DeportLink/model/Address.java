package com.deportlink.DeportLink.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    private long id;
    private String streetName;
    private int number;
    private String city;
    private String province;
    private int code;
    private double latitude;
    private double longitude;
    private User user;
}
