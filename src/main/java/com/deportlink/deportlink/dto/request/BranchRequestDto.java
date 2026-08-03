package com.deportlink.deportlink.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BranchRequestDto {

    @NotBlank(message = "El nombre de la sucursal no puede estar vacío")
    private String name;

    @NotNull(message = "La dirección es obligatoria")
    @Valid
    private AddressRequestDto addressRequestDto;

    @Positive(message = "El ID del club debe ser mayor a cero")
    private long idClub;
}