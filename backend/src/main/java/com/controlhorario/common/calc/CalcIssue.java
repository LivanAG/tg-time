package com.controlhorario.common.calc;

/**
 * Aviso o error de validación producido por los cálculos.
 *
 * @param code    código estable para el frontend (p. ej. LUNCH_BELOW_MINIMUM)
 * @param field   campo afectado, si aplica (p. ej. "endTime", "breaks[1]")
 * @param message texto para el usuario, en español
 */
public record CalcIssue(String code, String field, String message) {

    public static CalcIssue of(String code, String message) {
        return new CalcIssue(code, null, message);
    }
}
