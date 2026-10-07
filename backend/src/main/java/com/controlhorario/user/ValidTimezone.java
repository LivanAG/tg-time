package com.controlhorario.user;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Zona horaria IANA válida ({@code null} se acepta: combínala con @NotBlank si es obligatoria). */
@Documented
@Constraint(validatedBy = ValidTimezone.Validator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, PARAMETER})
@Retention(RUNTIME)
public @interface ValidTimezone {

    String message() default "Zona horaria no válida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTimezone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Timezones.isValid(value);
        }
    }
}
