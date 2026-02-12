package com.deportlink.deportlink.dto.response;


import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class PlayerResponseDto extends UserResponseDto {
    private Set<AddressResponseDto> addresses;
}
