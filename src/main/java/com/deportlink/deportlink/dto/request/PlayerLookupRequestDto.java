package com.deportlink.deportlink.dto.request;
import jakarta.validation.constraints.*;
public record PlayerLookupRequestDto(@NotBlank @Email @Size(max=255) String email) {}
