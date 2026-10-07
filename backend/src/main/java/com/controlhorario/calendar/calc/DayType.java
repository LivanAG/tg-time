package com.controlhorario.calendar.calc;

public enum DayType {
    LABORABLE,
    FIN_DE_SEMANA,
    FESTIVO,
    /** Día del mes que cae fuera del periodo (p. ej. 1-25 de mayo en un periodo que empieza el 26). */
    FUERA_DE_PERIODO
}
