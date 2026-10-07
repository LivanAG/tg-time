package com.controlhorario.common.web;

/** Error de validación de un campo dentro del ProblemDetail ({@code errors}). */
public record FieldErrorDto(String field, String message) {
}
