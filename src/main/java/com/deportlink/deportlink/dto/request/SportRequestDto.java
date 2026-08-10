package com.deportlink.deportlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SportRequestDto {

    @NotBlank(message = "El nombre del deporte no puede estar vacío")
    private String nameSport;
}
