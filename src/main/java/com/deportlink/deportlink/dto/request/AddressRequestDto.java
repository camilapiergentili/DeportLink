package com.deportlink.deportlink.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AddressRequestDto {

    @NotBlank(message = "El nombre de la calle no puede estar vacío")
    private String streetName;

    @Positive(message = "El número debe ser mayor a cero")
    private int number;

    @NotBlank(message = "La ciudad no puede estar vacía")
    private String city;

    @NotBlank(message = "La provincia no puede estar vacía")
    private String province;

    @Positive(message = "El código postal debe ser mayor a cero")
    private int postalCode;

    @DecimalMin(value = "-90.0", message = "La latitud debe ser mayor o igual a -90")
    @DecimalMax(value = "90.0",  message = "La latitud debe ser menor o igual a 90")
    private double latitude;

    @DecimalMin(value = "-180.0", message = "La longitud debe ser mayor o igual a -180")
    @DecimalMax(value = "180.0",  message = "La longitud debe ser menor o igual a 180")
    private double longitude;

}
