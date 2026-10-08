package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelCells.cell;
import static com.controlhorario.importexport.excel.ExcelReportLayout.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.summary.calc.AbsenceInput;
import com.controlhorario.summary.calc.DaySummary;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.summary.calc.MonthSummaryService;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.MixedTimes;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

class ExcelMonthWriterTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final PeriodRules RULES = ExcelFixture.rules();

    private static MonthSummary summarize(YearMonth month, List<WorkdayInput> workdays, List<AbsenceInput> absences) {
        return new MonthSummaryService().summarize(new PeriodCalendar(RULES), month, workdays, absences, 0,
                ExcelFixture.TODAY);
    }

    private static byte[] export(YearMonth month, List<WorkdayInput> workdays, List<AbsenceInput> absences,
            Map<LocalDate, String> notes) {
        return new ExcelMonthWriter().write(new ExcelMonthWriter.MonthData("Livan Aranda", "IZERTIS",
                "2026-2027 (26/05/2026 – 25/05/2027)", RULES, summarize(month, workdays, absences), notes));
    }

    private static byte[] export(YearMonth month, List<WorkdayInput> workdays) {
        return export(month, workdays, ExcelFixture.vacations(), Map.of());
    }

    private static List<WorkdayInput> realWorkdaysOf(YearMonth month) {
        return ExcelFixture.realWorkdays().stream().filter(w -> YearMonth.from(w.date()).equals(month)).toList();
    }

    private static ParsedWorkbook reimport(byte[] bytes) {
        return ExcelWorkbookReader.read(bytes, new ExcelWorkbookParser()::parse);
    }

    private static WorkdayInput day(LocalDate date, Location location, MixedTimes mixed, String start, String end,
            BreakInput... breaks) {
        return new WorkdayInput(date, start == null ? null : LocalTime.parse(start),
                end == null ? null : LocalTime.parse(end), List.of(breaks), location, mixed);
    }

    private static BreakInput br(BreakType type, String start, String end) {
        return new BreakInput(type, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void exportedJuneIsReadBackWithTheSameWorkdays() {
        List<WorkdayInput> june = realWorkdaysOf(JUNE);
        ParsedWorkbook parsed = reimport(export(JUNE, june));

        assertThat(parsed.sheets()).singleElement().satisfies(sheet -> {
            assertThat(sheet.name()).isEqualTo("Junio 2026");
            assertThat(sheet.month()).isEqualTo(JUNE);
            assertThat(sheet.exported()).isTrue();
            assertThat(sheet.rows()).isEqualTo(30);                  // todos los días del mes
            assertThat(sheet.dateCorrections()).isZero();
        });
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.settings()).isEqualTo(new DetectedSettings(20, 30, 50, 480, 420, null, null));

        Map<LocalDate, WorkdayInput> expected = june.stream()
                .collect(Collectors.toMap(WorkdayInput::date, Function.identity()));
        WorkdayCalculator calculator = new WorkdayCalculator(20, 30);
        assertThat(parsed.days()).hasSize(22);
        for (ParsedDay day : parsed.days()) {
            WorkdayInput original = expected.get(day.date());
            assertThat(day.startTime()).isEqualTo(original.start());
            assertThat(day.endTime()).isEqualTo(original.end());
            assertThat(day.breaks()).containsExactlyInAnyOrderElementsOf(original.breaks());
            assertThat(day.location()).isEqualTo(original.location());
            assertThat(day.representable()).isTrue();
            assertThat(day.errors()).isEmpty();
            assertThat(day.messages()).isEmpty();
            assertThat(day.excelWorkedMinutes()).isEqualTo(calculator.calculate(original).workedMinutes());
        }
    }

    @Test
    void theSheetHasAClearHeaderTotalsAndASummary() {
        List<WorkdayInput> june = realWorkdaysOf(JUNE);
        MonthSummary summary = summarize(JUNE, june, ExcelFixture.vacations());
        int week1Worked = summary.days().stream().filter(d -> d.date().isBefore(LocalDate.of(2026, 6, 8)))
                .mapToInt(DaySummary::workedMinutes).sum();

        Map<String, Object> cells = ExcelWorkbookReader.read(export(JUNE, june), wb -> {
            Sheet sheet = wb.getSheetAt(0);
            Map<String, Object> values = new HashMap<>();
            values.put("title", sheet.getRow(TITLE_ROW).getCell(COL_DATE).getStringCellValue());
            values.put("headers", headers(sheet));
            Row firstDay = sheet.getRow(FIRST_DATA_ROW);
            values.put("weekday", firstDay.getCell(COL_WEEKDAY).getStringCellValue());
            values.put("worked", ExcelCells.durationMinutes(firstDay.getCell(COL_WORKED)));
            values.put("rounded", ExcelCells.durationMinutes(firstDay.getCell(COL_ROUNDED)));
            // 1-7 de junio (lunes a domingo) y debajo el subtotal de la semana.
            Row week = sheet.getRow(FIRST_DATA_ROW + 7);
            values.put("week", week.getCell(COL_DATE).getStringCellValue());
            values.put("weekWorked", ExcelCells.totalMinutes(week.getCell(COL_WORKED)));
            for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String label = row == null || row.getCell(COL_DATE) == null ? ""
                        : row.getCell(COL_DATE).toString();
                if (TOTAL_LABEL.equals(label)) {
                    values.put("total", ExcelCells.totalMinutes(row.getCell(COL_WORKED)));
                    values.put("totalRounded", ExcelCells.totalMinutes(row.getCell(COL_ROUNDED)));
                } else if ("Teóricas".equals(label)) {
                    values.put("theoretical", ExcelCells.totalMinutes(row.getCell(COL_ABSENCE)));
                } else if ("= Saldo de cierre".equals(row == null || row.getCell(COL_BREAKFAST_START) == null ? ""
                        : row.getCell(COL_BREAKFAST_START).toString())) {
                    values.put("closing", row.getCell(COL_OTHER_BREAKS).getStringCellValue());
                }
            }
            values.put("frozen", sheet.getPaneInformation().getHorizontalSplitPosition());
            values.put("frozenColumns", sheet.getPaneInformation().getVerticalSplitPosition());
            values.put("landscape", sheet.getPrintSetup().getLandscape());
            return values;
        });

        assertThat(cells.get("title")).isEqualTo("Registro de jornada · Junio de 2026");
        assertThat(cells.get("headers")).isEqualTo(List.of("Fecha", "Día", "Ubicación", "Ausencia", "Oficina", "",
                "Casa", "", "Desayuno", "", "Comida", "", "Otras pausas", "Total", "Redondeado", "Notas"));
        assertThat(cells).containsEntry("weekday", "Lunes")
                .containsEntry("worked", 466)
                .containsEntry("rounded", summary.days().get(0).roundedMinutes())
                .containsEntry("weekWorked", week1Worked)
                .containsEntry("total", 9960)
                .containsEntry("totalRounded", summary.roundedMinutes())
                .containsEntry("theoretical", 9840)
                .containsEntry("closing", "+2:00")
                .containsEntry("landscape", true)
                .containsEntry("frozen", (short) FIRST_DATA_ROW)
                .containsEntry("frozenColumns", (short) 0);
        assertThat((String) cells.get("week")).startsWith("Semana 1 jun – 7 jun");
    }

    @Test
    void weekendsHolidaysAndAbsencesAreLabelledAndAbsencesComeBack() {
        YearMonth august = YearMonth.of(2026, 8);
        byte[] bytes = export(august, realWorkdaysOf(august));

        Map<LocalDate, String> labels = ExcelWorkbookReader.read(bytes, wb -> {
            Sheet sheet = wb.getSheetAt(0);
            Map<LocalDate, String> result = new HashMap<>();
            for (int r = FIRST_DATA_ROW; r < FIRST_DATA_ROW + 40; r++) {
                Row row = sheet.getRow(r);
                if (row != null && row.getCell(COL_DATE) != null
                        && row.getCell(COL_DATE).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                    LocalDate date = row.getCell(COL_DATE).getLocalDateTimeCellValue().toLocalDate();
                    result.put(date, row.getCell(COL_LOCATION).toString() + "|" + row.getCell(COL_ABSENCE));
                }
            }
            return result;
        });
        assertThat(labels.get(LocalDate.of(2026, 8, 1))).isEqualTo("Fin de semana|");
        assertThat(labels.get(LocalDate.of(2026, 8, 15))).startsWith("Fin de semana");   // la Asunción cae en sábado
        assertThat(labels.get(LocalDate.of(2026, 8, 17))).isEqualTo("|Vacaciones");

        ParsedWorkbook parsed = reimport(bytes);
        assertThat(parsed.absences()).hasSize(12)
                .allMatch(a -> a.type() == AbsenceType.VACACIONES && !a.halfDay());
        assertThat(parsed.companyMonthDates()).isEmpty();
    }

    @Test
    void mixedDaysNotesOtherBreaksAndHalfDayAbsencesSurviveTheRoundTrip() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate tuesday = LocalDate.of(2026, 10, 6);
        WorkdayInput mixed = day(monday, Location.MIXTO, new MixedTimes(LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalTime.of(13, 0), LocalTime.of(17, 0)), null, null,
                br(BreakType.DESAYUNO, "10:00", "10:25"), br(BreakType.COMIDA, "14:00", "14:20"),
                br(BreakType.OTRA, "15:00", "15:10"), br(BreakType.OTRA, "16:00", "16:05"));
        WorkdayInput home = day(tuesday, Location.CASA, null, "08:00", "12:00");
        List<AbsenceInput> absences = List.of(new AbsenceInput(tuesday, AbsenceType.VACACIONES, true));
        Map<LocalDate, String> notes = Map.of(monday, "Reunión con cliente");

        ParsedWorkbook parsed = reimport(export(OCTOBER, List.of(mixed, home), absences, notes));

        List<ParsedDay> days = new ArrayList<>(parsed.days());
        assertThat(days).hasSize(2);
        ParsedDay first = days.get(0);
        assertThat(first.location()).isEqualTo(Location.MIXTO);
        assertThat(first.mixed()).isEqualTo(mixed.mixed());
        assertThat(first.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(first.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(first.breaks()).containsExactlyElementsOf(mixed.breaks());
        assertThat(first.notes()).isEqualTo("Reunión con cliente");
        // 240 + 240 - 5 (desayuno) - 30 (comida mínima) - 15 (otras) = 430
        assertThat(first.excelWorkedMinutes()).isEqualTo(430);
        assertThat(first.errors()).isEmpty();

        ParsedDay second = days.get(1);
        assertThat(second.location()).isEqualTo(Location.CASA);
        assertThat(second.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(second.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(second.mixed()).isNull();
        assertThat(parsed.absences()).singleElement().satisfies(a -> {
            assertThat(a.date()).isEqualTo(tuesday);
            assertThat(a.type()).isEqualTo(AbsenceType.VACACIONES);
            assertThat(a.halfDay()).isTrue();
        });
    }

    @Test
    void rowsGrowToFitLongNotesAndSeveralOtherBreaks() {
        assertThat(ExcelMonthWriter.lines(null, COL_NOTES)).isEqualTo(1);
        assertThat(ExcelMonthWriter.lines("Reunión con cliente", COL_NOTES)).isEqualTo(1);
        assertThat(ExcelMonthWriter.lines("Primera línea\nSegunda línea", COL_NOTES)).isEqualTo(2);
        assertThat(ExcelMonthWriter.lines("15:30–15:40, 16:00–16:05", COL_OTHER_BREAKS)).isEqualTo(1);
        assertThat(ExcelMonthWriter.lines("15:30–15:40, 16:00–16:05, 17:00–17:10", COL_OTHER_BREAKS)).isEqualTo(2);

        LocalDate monday = LocalDate.of(2026, 10, 5);
        WorkdayInput office = day(monday, Location.OFICINA, null, "08:00", "15:00");
        String note = "Reunión de seguimiento con el cliente y revisión del sprint con todo el equipo";
        float height = ExcelWorkbookReader.read(export(OCTOBER, List.of(office), List.of(), Map.of(monday, note)),
                wb -> wb.getSheetAt(0).getRow(FIRST_DATA_ROW + 5).getHeightInPoints());   // 1-4 oct, subtotal, 5 oct
        assertThat(height).isGreaterThan(17);
    }

    private static List<String> headers(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row row = sheet.getRow(GROUP_HEADER_ROW);
        for (int c = 0; c <= LAST_COL; c++) {
            headers.add(row.getCell(c) == null ? "" : row.getCell(c).getStringCellValue());
        }
        return headers;
    }
}
