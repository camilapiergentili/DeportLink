package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerRequestDto extends UserRequestDto {

    private AddressRequestDto addressRequestDto;
}
