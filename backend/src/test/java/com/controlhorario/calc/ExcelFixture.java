package com.controlhorario.calc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calendar.calc.DateRange;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.summary.calc.AbsenceInput;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.WorkdayInput;

/**
 * Datos reales del Excel HORAS_IZERTIS_2026-27 (src/test/resources/excel/days.csv, una fila por
 * día de cada hoja mensual con la fecha recalculada a partir de la posición en la hoja).
 */
public final class ExcelFixture {

    /** "Hoy" de referencia: los fichajes posteriores del Excel son datos precargados, no reales. */
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    public record Row(LocalDate date, String sheet, int row, Map<String, String> cells) {

        String get(String column) {
            return cells.getOrDefault(column, "");
        }

        Integer minutes(String column) {
            String value = get(column);
            return value.isEmpty() ? null : Integer.valueOf(value);
        }

        LocalTime time(String column) {
            String value = get(column);
            return value.isEmpty() ? null : LocalTime.parse(value);
        }

        public boolean worked() {
            return !get("entrada").isEmpty();
        }

        /** Total Día del Excel (columna L), en minutos. */
        public int excelWorkedMinutes() {
            return Objects.requireNonNullElse(minutes("l_total"), 0);
        }

        /** Comida escrita a mano (columna J) sin horas de comida: no se puede representar como pausa. */
        public boolean lunchWithoutTimes() {
            return Objects.requireNonNullElse(minutes("j_comida"), 0) > 0 && time("com_ini") == null;
        }

        /**
         * Misma regla que el importador: si hay comida en J sin horas pero el "desayuno" empieza a
         * partir de las 14:00, ese tramo era en realidad la comida (caso del 29/09/2026).
         */
        public boolean representable() {
            return !lunchWithoutTimes() || (time("des_ini") != null && !time("des_ini").isBefore(LocalTime.of(14, 0)));
        }

        public WorkdayInput toWorkdayInput() {
            List<BreakInput> breaks = new ArrayList<>();
            LocalTime desIni = time("des_ini");
            LocalTime desFin = time("des_fin");
            boolean breakfastIsLunch = lunchWithoutTimes() && representable();
            if (desIni != null && desFin != null) {
                breaks.add(new BreakInput(breakfastIsLunch ? BreakType.COMIDA : BreakType.DESAYUNO, desIni, desFin));
            }
            if (time("com_ini") != null) {
                breaks.add(new BreakInput(BreakType.COMIDA, time("com_ini"), time("com_fin")));
            }
            if (time("otra_ini") != null) {
                breaks.add(new BreakInput(BreakType.OTRA, time("otra_ini"), time("otra_fin")));
            }
            Location location = switch (get("ubic")) {
                case "C" -> Location.CASA;
                case "M" -> Location.MIXTO;
                default -> Location.OFICINA;
            };
            return new WorkdayInput(date, time("entrada"), time("salida"), breaks, location, null, null, null);
        }
    }

    private static final List<Row> ROWS = load();

    private ExcelFixture() {
    }

    public static List<Row> rows() {
        return ROWS;
    }

    public static List<Row> workedRows(Predicate<Row> filter) {
        return ROWS.stream().filter(Row::worked).filter(filter).toList();
    }

    /** Fichajes reales: hasta {@link #TODAY} incluido. */
    public static List<WorkdayInput> realWorkdays() {
        return workedRows(r -> !r.date().isAfter(TODAY)).stream().map(Row::toWorkdayInput).toList();
    }

    /** Vacaciones del Excel: 10/07 (julio, J1=22 de 23) y 12 días de agosto. */
    public static List<AbsenceInput> vacations() {
        List<LocalDate> dates = new ArrayList<>(List.of(LocalDate.of(2026, 7, 10)));
        for (int day : new int[] {13, 14, 17, 18, 19, 20, 21, 24, 25, 26, 27, 28}) {
            dates.add(LocalDate.of(2026, 8, day));
        }
        return dates.stream().map(d -> new AbsenceInput(d, AbsenceType.VACACIONES, false)).toList();
    }

    /** Parámetros del periodo tal como los usa el Excel (hoja Horas y cabecera de cada mes). */
    public static PeriodRules rules() {
        return rules(Map.of());
    }

    public static PeriodRules rules(Map<String, Object> overrides) {
        Map<LocalDate, String> holidays = new LinkedHashMap<>();
        holidays.put(LocalDate.of(2026, 10, 12), "Fiesta Nacional de España");
        holidays.put(LocalDate.of(2026, 11, 2), "Lunes siguiente a Todos los Santos");
        holidays.put(LocalDate.of(2026, 11, 9), "Nuestra Señora de la Almudena");
        holidays.put(LocalDate.of(2026, 12, 7), "Lunes siguiente al Día de la Constitución");
        holidays.put(LocalDate.of(2026, 12, 8), "Inmaculada Concepción");
        holidays.put(LocalDate.of(2026, 12, 24), "Nochebuena (convenio)");
        holidays.put(LocalDate.of(2026, 12, 25), "Natividad del Señor");
        holidays.put(LocalDate.of(2026, 12, 31), "Nochevieja (convenio)");
        holidays.put(LocalDate.of(2027, 1, 1), "Año Nuevo");
        holidays.put(LocalDate.of(2027, 1, 6), "Epifanía del Señor");
        holidays.put(LocalDate.of(2027, 3, 19), "San José");
        holidays.put(LocalDate.of(2027, 3, 25), "Jueves Santo");
        holidays.put(LocalDate.of(2027, 3, 26), "Viernes Santo");
        int opening = (int) overrides.getOrDefault("openingBalanceMin", 0);
        return new PeriodRules(LocalDate.of(2026, 5, 26), LocalDate.of(2027, 5, 25), 1760 * 60, 23, 8 * 60, 7 * 60,
                20, 30, 15, 50, 8, opening,
                List.of(new DateRange(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 9, 15))), holidays);
    }

    public static PeriodCalendar calendar() {
        return new PeriodCalendar(rules());
    }

    private static List<Row> load() {
        try (var in = ExcelFixture.class.getResourceAsStream("/excel/days.csv");
                var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8))) {
            String[] header = reader.readLine().split(",", -1);
            List<Row> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> cells = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    cells.put(header[i], values[i]);
                }
                rows.add(new Row(LocalDate.parse(cells.get("date")), cells.get("sheet"),
                        Integer.parseInt(cells.get("row")), cells));
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
