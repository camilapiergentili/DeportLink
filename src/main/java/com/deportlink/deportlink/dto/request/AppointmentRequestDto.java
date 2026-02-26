package com.deportlink.deportlink.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AppointmentRequestDto {

    @NotNull
    @FutureOrPresent(message = "La fecha no puede ser anterior")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate day;

    @Positive
    private long idCourt;
}