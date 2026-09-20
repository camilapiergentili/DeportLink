package com.deportlink.deportlink.dto.request;

import com.deportlink.deportlink.dto.validation.PasswordMatches;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;


@Getter
@Setter
@PasswordMatches
public class UserRequestDto {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank
    private String confirmPassword;
    private String phone;

    // BCrypt (el encoder configurado en PasswordConfig) solo procesa los primeros 72 bytes de la
    // entrada y, desde Spring Security 6.3, lanza IllegalArgumentException en vez de truncar en
    // silencio si se le pasa más — eso caía sin controlar en el handler genérico de 500. @Size de
    // arriba cuenta caracteres (UTF-16 code units), no bytes: una contraseña con tildes o emojis
    // puede tener ≤72 caracteres y aun así superar los 72 bytes UTF-8 que BCrypt acepta. Este
    // chequeo mide bytes reales para cerrar ese caso.
    @AssertTrue(message = "La contraseña no debe superar 72 bytes UTF-8")
    public boolean isPasswordWithinEncoderLimit() {
        if (password == null) {
            return true; // @NotBlank ya reporta este caso
        }
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

}
