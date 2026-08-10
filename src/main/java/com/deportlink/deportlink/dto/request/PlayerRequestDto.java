package com.deportlink.deportlink.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerRequestDto extends UserRequestDto {

    @NotNull(message = "La dirección es obligatoria")
    @Valid
    private AddressRequestDto addressRequestDto;
}