package com.deportlink.deportlink.dto.request;
import com.deportlink.deportlink.enums.Level;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.time.*;
public record ClassSlotRequestDto(
    @NotNull @Positive Long courtId,
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull @Min(1) @Max(1440) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer durationMinutes,
    @NotNull Level level,
    @NotNull @Min(1) @Max(4) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer capacity) {
    @JsonIgnore
    @AssertTrue(message="La clase debe usar minutos enteros y no terminar después de medianoche")
    public boolean isValidTimeRange() {
        return startTime == null || durationMinutes == null ||
            (startTime.getSecond() == 0 && startTime.getNano() == 0 &&
             startTime.toSecondOfDay() + durationMinutes.longValue() * 60 <= 86400);
    }
}
