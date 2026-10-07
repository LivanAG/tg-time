package com.controlhorario.workday.calc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;

/**
 * Cálculo de un día (servicio puro, sin Spring):
 * <pre>trabajado = (salida - entrada) - max(0, desayuno - tolerancia) - max(comida, comidaMínima) - otrasPausas</pre>
 * La comida mínima solo se aplica si hubo pausa COMIDA. Ubicación: OFICINA todo oficina, CASA
 * todo casa, MIXTO los minutos en casa indicados y el resto oficina.
 */
public final class WorkdayCalculator {

    public static final String END_BEFORE_START = "END_BEFORE_START";
    public static final String BREAK_INVALID = "BREAK_INVALID";
    public static final String BREAK_OUTSIDE_WORKDAY = "BREAK_OUTSIDE_WORKDAY";
    public static final String BREAKS_OVERLAP = "BREAKS_OVERLAP";
    public static final String DUPLICATE_BREAKFAST = "DUPLICATE_BREAKFAST";
    public static final String DUPLICATE_LUNCH = "DUPLICATE_LUNCH";
    public static final String REMOTE_MINUTES_REQUIRED = "REMOTE_MINUTES_REQUIRED";
    public static final String REMOTE_MINUTES_EXCEED_WORKED = "REMOTE_MINUTES_EXCEED_WORKED";
    public static final String LUNCH_BELOW_MINIMUM = "LUNCH_BELOW_MINIMUM";

    private final int breakfastToleranceMin;
    private final int minLunchMin;

    public WorkdayCalculator(int breakfastToleranceMin, int minLunchMin) {
        this.breakfastToleranceMin = breakfastToleranceMin;
        this.minLunchMin = minLunchMin;
    }

    /** Errores que impiden guardar el día. Lista vacía si es válido. */
    public List<CalcIssue> validate(WorkdayInput input) {
        List<CalcIssue> errors = new ArrayList<>();
        if (input.start() == null || input.end() == null) {
            errors.add(new CalcIssue(END_BEFORE_START, "startTime", "La entrada y la salida son obligatorias"));
            return errors;
        }
        if (!input.end().isAfter(input.start())) {
            errors.add(new CalcIssue(END_BEFORE_START, "endTime", "La salida debe ser posterior a la entrada"));
            return errors;
        }
        List<BreakInput> breaks = input.breaks();
        int breakfasts = 0;
        int lunches = 0;
        for (int i = 0; i < breaks.size(); i++) {
            BreakInput b = breaks.get(i);
            String field = "breaks[" + i + "]";
            if (b.type() == null || b.start() == null || b.end() == null || !b.end().isAfter(b.start())) {
                errors.add(new CalcIssue(BREAK_INVALID, field, "La pausa necesita tipo y un fin posterior al inicio"));
                continue;
            }
            if (b.start().isBefore(input.start()) || b.end().isAfter(input.end())) {
                errors.add(new CalcIssue(BREAK_OUTSIDE_WORKDAY, field, "La pausa debe estar dentro de la jornada"));
            }
            if (b.type() == BreakType.DESAYUNO && ++breakfasts > 1) {
                errors.add(new CalcIssue(DUPLICATE_BREAKFAST, field, "Solo puede haber un desayuno"));
            }
            if (b.type() == BreakType.COMIDA && ++lunches > 1) {
                errors.add(new CalcIssue(DUPLICATE_LUNCH, field, "Solo puede haber una comida"));
            }
        }
        List<Integer> ordered = new ArrayList<>();
        for (int i = 0; i < breaks.size(); i++) {
            BreakInput b = breaks.get(i);
            if (b.start() != null && b.end() != null && b.end().isAfter(b.start())) {
                ordered.add(i);
            }
        }
        ordered.sort(Comparator.comparing(i -> breaks.get(i).start()));
        for (int k = 1; k < ordered.size(); k++) {
            BreakInput prev = breaks.get(ordered.get(k - 1));
            BreakInput cur = breaks.get(ordered.get(k));
            if (cur.start().isBefore(prev.end())) {
                errors.add(new CalcIssue(BREAKS_OVERLAP, "breaks[" + ordered.get(k) + "]", "Las pausas no pueden solaparse"));
            }
        }
        if (input.location() == Location.MIXTO) {
            if (input.remoteMinutes() == null) {
                errors.add(new CalcIssue(REMOTE_MINUTES_REQUIRED, "remoteMinutes",
                        "Indica cuántos minutos has trabajado en casa"));
            } else if (errors.isEmpty() && input.remoteMinutes() > calculate(input).workedMinutes()) {
                errors.add(new CalcIssue(REMOTE_MINUTES_EXCEED_WORKED, "remoteMinutes",
                        "Los minutos en casa no pueden superar el tiempo trabajado"));
            }
        }
        return errors;
    }

    /** Totales del día. Supone una entrada válida (ver {@link #validate}). */
    public WorkdayResult calculate(WorkdayInput input) {
        int gross = Minutes.between(input.start(), input.end());
        int breakfast = 0;
        int lunch = 0;
        boolean hasLunch = false;
        int other = 0;
        for (BreakInput b : input.breaks()) {
            int duration = Minutes.between(b.start(), b.end());
            switch (b.type()) {
                case DESAYUNO -> breakfast += duration;
                case COMIDA -> {
                    lunch += duration;
                    hasLunch = true;
                }
                case OTRA -> other += duration;
            }
        }
        int breakfastDeducted = Math.max(0, breakfast - breakfastToleranceMin);
        int lunchDeducted = hasLunch ? Math.max(lunch, minLunchMin) : 0;
        int worked = Math.max(0, gross - breakfastDeducted - lunchDeducted - other);

        List<CalcIssue> warnings = new ArrayList<>();
        if (hasLunch && lunch < minLunchMin) {
            warnings.add(new CalcIssue(LUNCH_BELOW_MINIMUM, "breaks",
                    "La comida dura " + lunch + " min: se descuenta el mínimo de " + minLunchMin + " min"));
        }

        int remote = switch (input.location()) {
            case OFICINA -> 0;
            case CASA -> worked;
            case MIXTO -> Math.min(worked, Math.max(0, input.remoteMinutes() == null ? 0 : input.remoteMinutes()));
        };
        return new WorkdayResult(gross, breakfast, breakfastDeducted, lunch, lunchDeducted, other, worked,
                worked - remote, remote, List.copyOf(warnings));
    }
}
