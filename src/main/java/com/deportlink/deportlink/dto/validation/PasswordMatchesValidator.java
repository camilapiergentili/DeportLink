package com.deportlink.deportlink.dto.validation;

import com.deportlink.deportlink.dto.request.UserRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, UserRequestDto> {

    @Override
    public boolean isValid(UserRequestDto dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getPassword() == null || dto.getConfirmPassword() == null) {
            // Campos ausentes: los reporta @NotBlank de cada uno, no esta constraint.
            return true;
        }

        boolean matches = dto.getPassword().equals(dto.getConfirmPassword());
        if (!matches) {
            // Sin esto, el error queda como error "global" del objeto y no aparece asociado
            // al campo confirmPassword en la respuesta (GlobalExceptionHandler solo recorre
            // getFieldErrors()).
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }
        return matches;
    }
}
