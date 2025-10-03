package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AddressRequestDto {

    private String streetName;
    private int number;
    private String city;
    private String province;
    private int code;
    private double latitude;
    private double longitude;

}
