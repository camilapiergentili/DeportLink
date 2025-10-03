package com.deportlink.DeportLink.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BranchResponseDto {

    private long id;
    private String name;
    private List<CourtResponseDto> courts;
    private AddressResponseDto address;
}
