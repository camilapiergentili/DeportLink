package com.deportlink.deportlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleRequestDto {

    @NotBlank(message = "El día no puede estar vacío")
    private String day;

    @NotBlank(message = "El horario de apertura no puede estar vacío")
    private String openingTime;

    @NotBlank(message = "El horario de cierre no puede estar vacío")
    private String closingTime;

    @NotNull(message = "La duración del turno es obligatoria")
    @Positive(message = "La duración del turno debe ser mayor a cero")
    private Long slotDuration;
}