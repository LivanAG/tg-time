package com.controlhorario.summary.calc;

import com.controlhorario.absence.Absence;

/** Entidad → entrada de cálculo. */
public final class AbsenceInputs {

    private AbsenceInputs() {
    }

    public static AbsenceInput from(Absence absence) {
        return new AbsenceInput(absence.getDate(), absence.getType(), absence.isHalfDay());
    }
}
