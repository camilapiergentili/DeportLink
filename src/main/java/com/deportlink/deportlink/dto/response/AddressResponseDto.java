package com.deportlink.deportlink.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressResponseDto {

    private String streetName;
    private int number;
    private String city;
    private String province;
    private int postalCode;
    private double latitude;
    private double longitude;

}
