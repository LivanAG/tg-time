package com.controlhorario.workday.calc;

import com.controlhorario.workday.Location;
import com.controlhorario.workday.Workday;

/** Entidad → entrada de cálculo. */
public final class WorkdayInputs {

    private WorkdayInputs() {
    }

    public static WorkdayInput from(Workday workday) {
        MixedTimes mixed = workday.getLocation() == Location.MIXTO
                ? new MixedTimes(workday.getOfficeStart(), workday.getOfficeEnd(), workday.getHomeStart(),
                        workday.getHomeEnd())
                : null;
        return new WorkdayInput(workday.getDate(), workday.getStartTime(), workday.getEndTime(),
                workday.getBreaks().stream()
                        .map(b -> new BreakInput(b.getType(), b.getStartTime(), b.getEndTime()))
                        .toList(),
                workday.getLocation(), mixed);
    }
}
