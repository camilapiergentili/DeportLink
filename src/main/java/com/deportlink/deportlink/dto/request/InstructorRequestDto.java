package com.deportlink.deportlink.dto.request;
import jakarta.validation.constraints.*;
public record InstructorRequestDto(
    @NotBlank @Size(max=255) String firstName,
    @NotBlank @Size(max=255) String lastName,
    @NotBlank @Email @Size(max=255) String email,
    @NotBlank @Size(min=8,max=72)
    @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
    String password,
    @Size(max=255) String phone) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message="La contraseña no debe superar 72 bytes UTF-8")
    public boolean isPasswordWithinEncoderLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
