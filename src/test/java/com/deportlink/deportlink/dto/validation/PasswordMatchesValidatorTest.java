package com.deportlink.deportlink.dto.validation;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la constraint @PasswordMatches directamente (sin Spring, sin MockMvc) sobre
 * PlayerRequestDto y OwnerRequestDto — ambas heredan la validación de UserRequestDto sin
 * declararla de nuevo.
 */
class PasswordMatchesValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static PlayerRequestDto validPlayerDto(String password, String confirmPassword) {
        PlayerRequestDto dto = new PlayerRequestDto();
        dto.setFirstName("Test");
        dto.setLastName("Player");
        dto.setEmail("player@example.com");
        dto.setPassword(password);
        dto.setConfirmPassword(confirmPassword);
        AddressRequestDto address = new AddressRequestDto();
        address.setStreetName("Av. Corrientes");
        address.setNumber(1234);
        address.setCity("CABA");
        address.setProvince("Buenos Aires");
        address.setPostalCode(1043);
        address.setLatitude(-34.6);
        address.setLongitude(-58.4);
        dto.setAddressRequestDto(address);
        return dto;
    }

    private static OwnerRequestDto validOwnerDto(String password, String confirmPassword) {
        OwnerRequestDto dto = new OwnerRequestDto();
        dto.setFirstName("Test");
        dto.setLastName("Owner");
        dto.setEmail("owner@example.com");
        dto.setPassword(password);
        dto.setConfirmPassword(confirmPassword);
        dto.setDni(20123456789L);
        dto.setCuil("20123456789");
        dto.setDateOfBirth("01/01/1985");
        return dto;
    }

    @Test
    void player_passwordsIguales_sinViolaciones() {
        Set<ConstraintViolation<PlayerRequestDto>> violations =
                validator.validate(validPlayerDto("password123", "password123"));

        assertThat(violations).isEmpty();
    }

    @Test
    void player_passwordsDistintas_violacionEnConfirmPassword() {
        Set<ConstraintViolation<PlayerRequestDto>> violations =
                validator.validate(validPlayerDto("password123", "otraPassword"));

        assertThat(violations).hasSize(1);
        ConstraintViolation<PlayerRequestDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("confirmPassword");
        assertThat(violation.getMessage()).isEqualTo("Las contraseñas no coinciden");
    }

    @Test
    void owner_passwordsIguales_sinViolaciones() {
        Set<ConstraintViolation<OwnerRequestDto>> violations =
                validator.validate(validOwnerDto("password123", "password123"));

        assertThat(violations).isEmpty();
    }

    @Test
    void owner_passwordsDistintas_violacionEnConfirmPassword() {
        Set<ConstraintViolation<OwnerRequestDto>> violations =
                validator.validate(validOwnerDto("password123", "otraPassword"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("confirmPassword");
    }

    @Test
    void confirmPasswordAusente_noDuplicaError_loReportaSoloNotBlank() {
        PlayerRequestDto dto = validPlayerDto("password123", null);

        Set<ConstraintViolation<PlayerRequestDto>> violations = validator.validate(dto);

        // Solo @NotBlank de confirmPassword — @PasswordMatches no debe agregar un segundo error.
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("confirmPassword");
    }
}
