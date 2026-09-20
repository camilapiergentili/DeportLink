package com.deportlink.deportlink.dto.request;
import jakarta.validation.constraints.*;
public record ClassPlayerRequestDto(@NotNull @Positive Long playerId) {}
