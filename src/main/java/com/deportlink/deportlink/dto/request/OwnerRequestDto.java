package com.deportlink.deportlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OwnerRequestDto extends UserRequestDto {

    @Positive
    private long dni;

    @NotBlank
    private String dateOfBirth;

    @NotBlank
    private String cuil;
}