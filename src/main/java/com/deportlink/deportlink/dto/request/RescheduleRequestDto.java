package com.deportlink.deportlink.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class RescheduleRequestDto {

    @NotNull
    @Future(message = "La nueva fecha debe ser futura")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate newDay;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime newStartTime;

}