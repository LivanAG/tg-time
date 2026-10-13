package com.controlhorario.workday.calc;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;

/**
 * Cálculo de un día (servicio puro, sin Spring):
 * <pre>trabajado = bruto - max(0, desayuno - tolerancia) - max(comida, comidaMínima) - otrasPausas</pre>
 * El bruto es la suma de los tramos trabajados: entrada-salida o, en MIXTO, el tramo de oficina más el
 * de casa (el hueco entre ellos no cuenta). La comida mínima solo se aplica si hubo pausa COMIDA.
 * Ubicación: OFICINA todo oficina, CASA todo casa, MIXTO lo trabajado en el tramo de casa (su duración
 * menos lo que se descuenta de las pausas que caen en él) y el resto oficina.
 * <p>
 * <b>Horario computable</b> ({@code countFrom}-{@code countUntil}, p. ej. 07:00-20:00): se puede fichar
 * fuera de él, pero solo cuenta lo que cae dentro. Tramos y pausas se recortan a ese horario antes de
 * calcular; una pausa que queda fuera no descuenta. La comida mínima se aplica a la comida fichada y
 * solo se descuenta la parte que cae dentro del horario.
 */
public final class WorkdayCalculator {

    public static final String END_BEFORE_START = "END_BEFORE_START";
    public static final String SEGMENTS_OVERLAP = "SEGMENTS_OVERLAP";
    public static final String BREAK_INVALID = "BREAK_INVALID";
    public static final String BREAK_OUTSIDE_WORKDAY = "BREAK_OUTSIDE_WORKDAY";
    public static final String BREAKS_OVERLAP = "BREAKS_OVERLAP";
    public static final String DUPLICATE_BREAKFAST = "DUPLICATE_BREAKFAST";
    public static final String DUPLICATE_LUNCH = "DUPLICATE_LUNCH";
    public static final String LUNCH_BELOW_MINIMUM = "LUNCH_BELOW_MINIMUM";
    public static final String OUTSIDE_COUNTED_HOURS = "OUTSIDE_COUNTED_HOURS";

    private static final int DAY_MINUTES = 24 * 60;

    /** Tramo trabajado del día, en un sitio. */
    private record Segment(Location location, LocalTime start, LocalTime end) {

        boolean contains(BreakInput b) {
            return !b.start().isBefore(start) && !b.end().isAfter(end);
        }
    }

    private final int breakfastToleranceMin;
    private final int minLunchMin;
    /** Horario computable en minutos del día; sin límites, 0-1440. */
    private final int countFromMin;
    private final int countUntilMin;

    /** Sin horario computable: cuenta todo lo fichado. */
    public WorkdayCalculator(int breakfastToleranceMin, int minLunchMin) {
        this(breakfastToleranceMin, minLunchMin, null, null);
    }

    /** {@code countFrom}/{@code countUntil} null = sin límite por ese lado. */
    public WorkdayCalculator(int breakfastToleranceMin, int minLunchMin, LocalTime countFrom, LocalTime countUntil) {
        this.breakfastToleranceMin = breakfastToleranceMin;
        this.minLunchMin = minLunchMin;
        this.countFromMin = countFrom == null ? 0 : Minutes.of(countFrom);
        this.countUntilMin = countUntil == null ? DAY_MINUTES : Minutes.of(countUntil);
    }

    /** Calculadora con las reglas del periodo (tolerancia, comida mínima y horario computable). */
    public static WorkdayCalculator of(PeriodRules rules) {
        return new WorkdayCalculator(rules.breakfastToleranceMin(), rules.minLunchMin(), rules.countFrom(),
                rules.countUntil());
    }

    /** Errores que impiden guardar el día. Lista vacía si es válido. */
    public List<CalcIssue> validate(WorkdayInput input) {
        List<CalcIssue> errors = input.location() == Location.MIXTO ? validateMixed(input.mixed())
                : validateStartEnd(input);
        if (!errors.isEmpty()) {
            return errors;
        }
        List<Segment> segments = segments(input);
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
            if (segments.stream().noneMatch(s -> s.contains(b))) {
                errors.add(new CalcIssue(BREAK_OUTSIDE_WORKDAY, field, input.location() == Location.MIXTO
                        ? "La pausa debe estar dentro del tramo de oficina o del de casa"
                        : "La pausa debe estar dentro de la jornada"));
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
        return errors;
    }

    private static List<CalcIssue> validateStartEnd(WorkdayInput input) {
        List<CalcIssue> errors = new ArrayList<>();
        if (input.start() == null || input.end() == null) {
            errors.add(new CalcIssue(END_BEFORE_START, "startTime", "La entrada y la salida son obligatorias"));
        } else if (!input.end().isAfter(input.start())) {
            errors.add(new CalcIssue(END_BEFORE_START, "endTime", "La salida debe ser posterior a la entrada"));
        }
        return errors;
    }

    private static List<CalcIssue> validateMixed(MixedTimes mixed) {
        List<CalcIssue> errors = new ArrayList<>();
        MixedTimes m = mixed == null ? new MixedTimes(null, null, null, null) : mixed;
        if (m.officeStart() == null || m.officeEnd() == null) {
            errors.add(new CalcIssue(END_BEFORE_START, "officeStart", "Indica la entrada y la salida en la oficina"));
        } else if (!m.officeEnd().isAfter(m.officeStart())) {
            errors.add(new CalcIssue(END_BEFORE_START, "officeEnd",
                    "La salida de la oficina debe ser posterior a la entrada"));
        }
        if (m.homeStart() == null || m.homeEnd() == null) {
            errors.add(new CalcIssue(END_BEFORE_START, "homeStart", "Indica la entrada y la salida en casa"));
        } else if (!m.homeEnd().isAfter(m.homeStart())) {
            errors.add(new CalcIssue(END_BEFORE_START, "homeEnd", "La salida de casa debe ser posterior a la entrada"));
        }
        if (errors.isEmpty() && m.officeStart().isBefore(m.homeEnd()) && m.homeStart().isBefore(m.officeEnd())) {
            errors.add(new CalcIssue(SEGMENTS_OVERLAP, "homeStart",
                    "Los tramos de oficina y de casa no pueden solaparse"));
        }
        return errors;
    }

    /** Tramos trabajados: entrada-salida o, en MIXTO, oficina y casa. Supone horas válidas. */
    private static List<Segment> segments(WorkdayInput input) {
        if (input.location() == Location.MIXTO) {
            MixedTimes m = input.mixed();
            return List.of(new Segment(Location.OFICINA, m.officeStart(), m.officeEnd()),
                    new Segment(Location.CASA, m.homeStart(), m.homeEnd()));
        }
        return List.of(new Segment(input.location(), input.start(), input.end()));
    }

    /** Minutos de [start, end) que caen dentro del horario computable. */
    private int counted(LocalTime start, LocalTime end) {
        return Math.max(0, Math.min(Minutes.of(end), countUntilMin) - Math.max(Minutes.of(start), countFromMin));
    }

    /** Totales del día. Supone una entrada válida (ver {@link #validate}). */
    public WorkdayResult calculate(WorkdayInput input) {
        List<Segment> segments = segments(input);
        int gross = segments.stream().mapToInt(s -> counted(s.start(), s.end())).sum();
        int breakfast = 0;
        int lunch = 0;
        int lunchRecorded = 0;
        boolean hasLunch = false;
        int other = 0;
        for (BreakInput b : input.breaks()) {
            int duration = counted(b.start(), b.end());
            switch (b.type()) {
                case DESAYUNO -> breakfast += duration;
                case COMIDA -> {
                    lunch += duration;
                    lunchRecorded += Minutes.between(b.start(), b.end());
                    hasLunch = true;
                }
                case OTRA -> other += duration;
            }
        }
        int breakfastDeducted = Math.max(0, breakfast - breakfastToleranceMin);
        // La comida mínima vale para la comida fichada; se descuenta lo que cae dentro del horario.
        int lunchDeducted = hasLunch && lunch > 0
                ? Math.max(lunchRecorded, minLunchMin) - (lunchRecorded - lunch) : 0;
        int worked = Math.max(0, gross - breakfastDeducted - lunchDeducted - other);

        List<CalcIssue> warnings = new ArrayList<>();
        if (hasLunch && lunch > 0 && lunchRecorded < minLunchMin) {
            warnings.add(new CalcIssue(LUNCH_BELOW_MINIMUM, "breaks",
                    "La comida dura " + lunchRecorded + " min: se descuenta el mínimo de " + minLunchMin + " min"));
        }
        warnings.addAll(outsideWarnings(input, segments));

        int remote = switch (input.location()) {
            case OFICINA -> 0;
            case CASA -> worked;
            case MIXTO -> {
                // Lo trabajado en casa: el tramo menos lo que se descuenta de las pausas que caen en él
                // (solo hay un desayuno y una comida, así que su descuento va entero a su tramo).
                Segment home = segments.get(1);
                int homeWorked = counted(home.start(), home.end());
                for (BreakInput b : input.breaks()) {
                    if (home.contains(b)) {
                        homeWorked -= switch (b.type()) {
                            case DESAYUNO -> breakfastDeducted;
                            case COMIDA -> lunchDeducted;
                            case OTRA -> counted(b.start(), b.end());
                        };
                    }
                }
                yield Math.min(worked, Math.max(0, homeWorked));
            }
        };
        return new WorkdayResult(gross, breakfast, breakfastDeducted, lunch, lunchDeducted, other, worked,
                worked - remote, remote, List.copyOf(warnings));
    }

    /** Avisos (no impiden guardar) si se fichó antes o después del horario computable. */
    private List<CalcIssue> outsideWarnings(WorkdayInput input, List<Segment> segments) {
        boolean mixed = input.location() == Location.MIXTO;
        List<CalcIssue> warnings = new ArrayList<>();
        Segment first = segments.stream().min(Comparator.comparing(Segment::start)).orElseThrow();
        Segment last = segments.stream().max(Comparator.comparing(Segment::end)).orElseThrow();
        if (Minutes.of(first.start()) < countFromMin) {
            String field = !mixed ? "startTime" : first.location() == Location.CASA ? "homeStart" : "officeStart";
            warnings.add(new CalcIssue(OUTSIDE_COUNTED_HOURS, field,
                    "Antes de las " + Minutes.format(countFromMin) + " no cuenta: se computa desde las "
                            + Minutes.format(countFromMin)));
        }
        if (Minutes.of(last.end()) > countUntilMin) {
            String field = !mixed ? "endTime" : last.location() == Location.CASA ? "homeEnd" : "officeEnd";
            warnings.add(new CalcIssue(OUTSIDE_COUNTED_HOURS, field,
                    "Después de las " + Minutes.format(countUntilMin) + " no cuenta: se computa hasta las "
                            + Minutes.format(countUntilMin)));
        }
        return warnings;
    }
}
