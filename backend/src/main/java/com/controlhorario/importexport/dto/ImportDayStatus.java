package com.controlhorario.importexport.dto;

/** Estado de un día del Excel en la vista previa de la importación. */
public enum ImportDayStatus {
    /** Hay fichaje, fecha ≤ hoy, dentro del periodo y sin registro previo. */
    NEW,
    /** Ya hay un registro ese día. */
    EXISTS,
    /** Fecha posterior a hoy (dato precargado, no real). */
    FUTURE,
    /** Fuera del periodo elegido. */
    OUT_OF_PERIOD,
    /** No pasa la validación del cálculo o tiene celdas ilegibles. */
    INVALID,
    /** Comida escrita a mano sin horas: no se puede representar con pausas reales. */
    NOT_REPRESENTABLE
}
