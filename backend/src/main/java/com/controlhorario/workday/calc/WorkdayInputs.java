package com.controlhorario.workday.calc;

import com.controlhorario.workday.Workday;

/** Entidad → entrada de cálculo. */
public final class WorkdayInputs {

    private WorkdayInputs() {
    }

    public static WorkdayInput from(Workday workday) {
        return new WorkdayInput(workday.getDate(), workday.getStartTime(), workday.getEndTime(),
                workday.getBreaks().stream()
                        .map(b -> new BreakInput(b.getType(), b.getStartTime(), b.getEndTime()))
                        .toList(),
                workday.getLocation(), workday.getRemoteMinutes());
    }
}
