package com.deportlink.deportlink.dto.request;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CourtRequestDto {

    private String name;
    private double pricePerHour;
    private long idBranch;
    private long idSport;

}
