package com.deportlink.deportlink.dto.request;

import com.deportlink.deportlink.model.ClubType;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ClubRequestDto {

    private String name;
    private String legalName;
    private ClubType clubType;
    private String cuit;
    private Set<Long> ownerIds;

}
