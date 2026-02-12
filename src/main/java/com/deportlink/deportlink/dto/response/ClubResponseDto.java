package com.deportlink.deportlink.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
public class ClubResponseDto {

    private long id;
    private String name;
    private Set<BranchResponseDto> branches;

}
