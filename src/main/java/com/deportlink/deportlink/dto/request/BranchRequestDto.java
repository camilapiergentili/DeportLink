package com.deportlink.deportlink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BranchRequestDto {

    private String name;
    private AddressRequestDto addressRequestDto;
    private long idClub;
}
