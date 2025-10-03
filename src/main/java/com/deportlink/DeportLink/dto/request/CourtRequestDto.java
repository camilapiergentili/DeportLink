package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class CourtRequestDto {

    private String name;
    private double pricePerHour;
    private long idBranch;
    private long idSport;
}
