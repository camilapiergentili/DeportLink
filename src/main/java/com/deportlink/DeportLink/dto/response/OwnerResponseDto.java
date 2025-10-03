package com.deportlink.DeportLink.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class OwnerResponseDto extends UserResponseDto {

    private List<ClubResponseDto> clubs;

}
