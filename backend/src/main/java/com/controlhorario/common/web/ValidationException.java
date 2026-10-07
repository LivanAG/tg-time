package com.controlhorario.common.web;

import java.util.List;

/** 400 con la lista de errores por campo (validaciones de negocio fuera de Bean Validation). */
public class ValidationException extends RuntimeException {

    private final List<FieldErrorDto> errors;

    public ValidationException(List<FieldErrorDto> errors) {
        super(errors.isEmpty() ? "Datos no válidos" : errors.get(0).message());
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String field, String message) {
        this(List.of(new FieldErrorDto(field, message)));
    }

    public List<FieldErrorDto> getErrors() {
        return errors;
    }
}
