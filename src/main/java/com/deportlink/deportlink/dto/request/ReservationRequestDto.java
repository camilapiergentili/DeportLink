package com.deportlink.deportlink.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationRequestDto {

    @NotNull
    @FutureOrPresent
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate day;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @Positive
    private long idCourt;

    @Positive
    private long idPlayer;
}