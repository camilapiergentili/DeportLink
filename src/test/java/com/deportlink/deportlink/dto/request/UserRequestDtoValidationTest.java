package com.deportlink.deportlink.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el límite de 72 bytes UTF-8 que BCrypt acepta (ver docs/software-review-2026-09-11.md,
 * finding N1), directamente contra un Validator real (sin Spring, sin MockMvc) sobre
 * PlayerRequestDto y OwnerRequestDto — ambas heredan el campo password de UserRequestDto sin
 * declararlo de nuevo. El caso no-ASCII es el que justifica el @AssertTrue: @Size cuenta
 * caracteres (UTF-16 code units), no bytes, así que por sí solo no detecta una contraseña de
 * ≤72 caracteres que ocupa más de 72 bytes UTF-8.
 */
class UserRequestDtoValidationTest {

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

    private static PlayerRequestDto playerDtoWithPassword(String password) {
        PlayerRequestDto dto = new PlayerRequestDto();
        dto.setFirstName("Test");
        dto.setLastName("Player");
        dto.setEmail("player@example.com");
        dto.setPassword(password);
        dto.setConfirmPassword(password);
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

    private static OwnerRequestDto ownerDtoWithPassword(String password) {
        OwnerRequestDto dto = new OwnerRequestDto();
        dto.setFirstName("Test");
        dto.setLastName("Owner");
        dto.setEmail("owner@example.com");
        dto.setPassword(password);
        dto.setConfirmPassword(password);
        dto.setDni(20123456789L);
        dto.setCuil("20123456789");
        dto.setDateOfBirth("01/01/1985");
        return dto;
    }

    private static <T> Set<String> propertyPaths(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    // --- PlayerRequestDto ---

    @Test
    void player_passwordDeSetentaYDosBytesAscii_sinViolaciones() {
        String password = "a".repeat(72);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        Set<ConstraintViolation<PlayerRequestDto>> violations =
                validator.validate(playerDtoWithPassword(password));

        assertThat(violations).isEmpty();
    }

    @Test
    void player_passwordDeSetentaYTresBytesAscii_violaSizeYLimiteDeBytes() {
        String password = "a".repeat(73);

        Set<ConstraintViolation<PlayerRequestDto>> violations =
                validator.validate(playerDtoWithPassword(password));

        assertThat(propertyPaths(violations)).containsExactlyInAnyOrder("password", "passwordWithinEncoderLimit");
    }

    @Test
    void player_passwordDeSetentaYDosCaracteresNoAscii_superaSetentaYDosBytes_violaSoloElLimiteDeBytes() {
        String password = "á".repeat(72); // 72 caracteres, 144 bytes UTF-8: @Size(max=72) no lo detecta
        assertThat(password).hasSize(72);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);

        Set<ConstraintViolation<PlayerRequestDto>> violations =
                validator.validate(playerDtoWithPassword(password));

        assertThat(violations).hasSize(1);
        ConstraintViolation<PlayerRequestDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("passwordWithinEncoderLimit");
        assertThat(violation.getMessage()).isEqualTo("La contraseña no debe superar 72 bytes UTF-8");
    }

    // --- OwnerRequestDto (misma clase base, mismo campo heredado) ---

    @Test
    void owner_passwordDeSetentaYDosBytesAscii_sinViolaciones() {
        String password = "a".repeat(72);

        Set<ConstraintViolation<OwnerRequestDto>> violations =
                validator.validate(ownerDtoWithPassword(password));

        assertThat(violations).isEmpty();
    }

    @Test
    void owner_passwordDeSetentaYTresBytesAscii_violaSizeYLimiteDeBytes() {
        String password = "a".repeat(73);

        Set<ConstraintViolation<OwnerRequestDto>> violations =
                validator.validate(ownerDtoWithPassword(password));

        assertThat(propertyPaths(violations)).containsExactlyInAnyOrder("password", "passwordWithinEncoderLimit");
    }

    @Test
    void owner_passwordDeSetentaYDosCaracteresNoAscii_superaSetentaYDosBytes_violaSoloElLimiteDeBytes() {
        String password = "á".repeat(72);

        Set<ConstraintViolation<OwnerRequestDto>> violations =
                validator.validate(ownerDtoWithPassword(password));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("passwordWithinEncoderLimit");
    }
}
