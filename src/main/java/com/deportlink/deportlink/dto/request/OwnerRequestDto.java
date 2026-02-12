package com.deportlink.deportlink.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OwnerRequestDto extends UserRequestDto {

    private long dni;
    private String dateOfBirth;
    private String cuil;

}
