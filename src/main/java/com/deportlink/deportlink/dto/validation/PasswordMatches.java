package com.deportlink.deportlink.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que {@code password} y {@code confirmPassword} coincidan. Se aplica a nivel de clase
 * (no de campo) porque compara dos campos entre sí — ver {@link PasswordMatchesValidator}.
 * <p>
 * Declarada sobre {@code UserRequestDto}; Bean Validation la aplica también a sus subclases
 * ({@code PlayerRequestDto}, {@code OwnerRequestDto}) sin necesidad de repetirla.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchesValidator.class)
public @interface PasswordMatches {
    String message() default "Las contraseñas no coinciden";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
