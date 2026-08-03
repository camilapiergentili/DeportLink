package com.deportlink.deportlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourtRequestDto {

    @NotBlank(message = "El nombre de la cancha no puede estar vacío")
    private String name;

    @PositiveOrZero(message = "El precio por hora no puede ser negativo")
    private double pricePerHour;

    @Positive(message = "El ID de la sucursal debe ser mayor a cero")
    private long idBranch;

    @Positive(message = "El ID del deporte debe ser mayor a cero")
    private long idSport;

}