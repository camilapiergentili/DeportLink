package com.deportlink.DeportLink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OwnerRequestDto extends UserRequestDto {

    private String dni;
    private String cuil;

}
