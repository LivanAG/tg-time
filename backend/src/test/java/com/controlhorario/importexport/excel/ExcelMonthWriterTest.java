package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelCells.cell;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.summary.calc.DaySummary;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.summary.calc.MonthSummaryService;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;

import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

class ExcelMonthWriterTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final PeriodRules RULES = ExcelFixture.rules();

    private static MonthSummary summarize(YearMonth month, List<WorkdayInput> workdays) {
        return new MonthSummaryService().summarize(new PeriodCalendar(RULES), month, workdays,
                ExcelFixture.vacations(), 0, ExcelFixture.TODAY);
    }

    private static byte[] export(YearMonth month, List<WorkdayInput> workdays) {
        return new ExcelMonthWriter().write(new ExcelMonthWriter.MonthData("Livan Aranda", "IZERTIS", RULES,
                summarize(month, workdays), workdays));
    }

    @Test
    void exportedJuneIsReadBackWithTheSameWorkdays() {
        List<WorkdayInput> june = ExcelFixture.realWorkdays().stream()
                .filter(w -> YearMonth.from(w.date()).equals(JUNE))
                .toList();
        ParsedWorkbook parsed = ExcelWorkbookReader.read(export(JUNE, june), new ExcelWorkbookParser()::parse);

        assertThat(parsed.sheets()).singleElement().satisfies(sheet -> {
            assertThat(sheet.name()).isEqualTo("Junio 2026");
            assertThat(sheet.month()).isEqualTo(JUNE);
            assertThat(sheet.rows()).isEqualTo(22);
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
    void footerAndSubtotalsComeFromTheMonthSummary() {
        List<WorkdayInput> june = ExcelFixture.realWorkdays().stream()
                .filter(w -> YearMonth.from(w.date()).equals(JUNE))
                .toList();
        MonthSummary summary = summarize(JUNE, june);
        int week1Worked = summary.days().stream().filter(d -> d.date().isBefore(LocalDate.of(2026, 6, 8)))
                .mapToInt(DaySummary::workedMinutes).sum();
        int rounded = summary.days().stream().mapToInt(DaySummary::roundedMinutes).sum();

        Map<String, Integer> cells = ExcelWorkbookReader.read(export(JUNE, june), wb -> {
            Sheet sheet = wb.getSheetAt(0);
            return Map.of(
                    "F1", ExcelCells.durationMinutes(cell(sheet, "F1")),
                    "J1", ExcelCells.integer(cell(sheet, "J1")),
                    "L1", ExcelCells.integer(cell(sheet, "L1")),
                    "L7", ExcelCells.durationMinutes(cell(sheet, "L7")),
                    "P7", ExcelCells.durationMinutes(cell(sheet, "P7")),
                    "L12", ExcelCells.totalMinutes(cell(sheet, "L12")),
                    "L43", ExcelCells.totalMinutes(cell(sheet, "L43")),
                    "P43", ExcelCells.totalMinutes(cell(sheet, "P43")),
                    "C46", ExcelCells.totalMinutes(cell(sheet, "C46")),
                    "C47", ExcelCells.totalMinutes(cell(sheet, "C47")));
        });

        // Junio 2026: 10 días a 8 h y 12 a 7 h = 164 h; hechas 166 h (hoja Horas del Excel).
        assertThat(summary.theoreticalMinutes()).isEqualTo(9840);
        assertThat(summary.workedMinutes()).isEqualTo(9960);
        assertThat(cells).containsEntry("F1", 30)
                .containsEntry("J1", 10)
                .containsEntry("L1", 12)
                .containsEntry("L7", 466)
                .containsEntry("P7", summary.days().get(0).roundedMinutes())
                .containsEntry("L12", week1Worked)
                .containsEntry("L43", 9960)
                .containsEntry("P43", rounded)
                .containsEntry("C46", 9840)
                .containsEntry("C47", 9960);
        Map<String, Object> footer = ExcelWorkbookReader.read(export(JUNE, june), wb -> {
            Sheet sheet = wb.getSheetAt(0);
            return Map.of(
                    "C48", ExcelCells.totalMinutes(cell(sheet, "C48")),
                    "C49", ExcelCells.totalMinutes(cell(sheet, "C49")),
                    "C53", cell(sheet, "C53").getNumericCellValue(),
                    "A12", cell(sheet, "A12").getStringCellValue());
        });
        assertThat(footer).containsEntry("C48", 0).containsEntry("C49", 120).containsEntry("C53", 0.0)
                .containsEntry("A12", "Semana 1");
    }

    @Test
    void augustExportCountsItsVacations() {
        YearMonth august = YearMonth.of(2026, 8);
        List<WorkdayInput> workdays = ExcelFixture.realWorkdays().stream()
                .filter(w -> YearMonth.from(w.date()).equals(august))
                .toList();
        double vacations = ExcelWorkbookReader.read(export(august, workdays),
                wb -> cell(wb.getSheetAt(0), "C53").getNumericCellValue());
        assertThat(vacations).isEqualTo(12.0);
    }

    @Test
    void mixedLocationAndOtherBreaksSurviveTheRoundTrip() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        WorkdayInput mixed = new WorkdayInput(date, LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakInput(BreakType.DESAYUNO, LocalTime.of(10, 0), LocalTime.of(10, 25)),
                        new BreakInput(BreakType.COMIDA, LocalTime.of(14, 0), LocalTime.of(14, 20)),
                        new BreakInput(BreakType.OTRA, LocalTime.of(16, 0), LocalTime.of(16, 10))),
                Location.MIXTO, 120);
        byte[] bytes = export(YearMonth.of(2026, 10), List.of(mixed));

        ParsedDay day = ExcelWorkbookReader.read(bytes, new ExcelWorkbookParser()::parse).days().get(0);

        assertThat(day.date()).isEqualTo(date);
        assertThat(day.breaks()).containsExactlyElementsOf(mixed.breaks());
        assertThat(day.location()).isEqualTo(Location.MIXTO);
        assertThat(day.remoteMinutes()).isEqualTo(120);
        // 540 - 5 (desayuno) - 30 (comida mínima) - 10 (otra) = 495
        assertThat(day.excelWorkedMinutes()).isEqualTo(495);
        assertThat(day.representable()).isTrue();
    }
}
