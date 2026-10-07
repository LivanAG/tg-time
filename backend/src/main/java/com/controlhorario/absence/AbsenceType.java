package com.controlhorario.absence;

public enum AbsenceType {
    VACACIONES,
    /** Puente recuperable: no se trabaja pero sus horas se restan del saldo. */
    PUENTE,
    PERMISO,
    BAJA
}
