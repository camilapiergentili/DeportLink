package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ClubRequestDto {

    private String name;
    private String legalName;
    private String clubType;
    private String cuit;
    private Set<Long> ownerIds;

}
