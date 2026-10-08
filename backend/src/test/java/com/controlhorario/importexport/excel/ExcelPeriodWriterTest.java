package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelReportLayout.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.summary.calc.MonthRow;
import com.controlhorario.summary.calc.MonthSummaryService;
import com.controlhorario.summary.calc.PeriodSummary;
import com.controlhorario.summary.calc.PeriodSummaryService;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

class ExcelPeriodWriterTest {

    private static final PeriodCalendar CALENDAR = ExcelFixture.calendar();
    private static final String PERIOD_NAME = "2026-2027 (26/05/2026 – 25/05/2027)";

    private static PeriodSummary periodSummary() {
        return new PeriodSummaryService().summarize(CALENDAR, ExcelFixture.realWorkdays(), ExcelFixture.vacations(),
                ExcelFixture.TODAY);
    }

    private static byte[] export(PeriodSummary summary) {
        List<ExcelMonthWriter.MonthData> months = new ArrayList<>();
        for (YearMonth m = YearMonth.of(2026, 5); !m.isAfter(YearMonth.of(2027, 5)); m = m.plusMonths(1)) {
            months.add(new ExcelMonthWriter.MonthData("Livan Aranda", "IZERTIS", PERIOD_NAME, ExcelFixture.rules(),
                    new MonthSummaryService().summarize(CALENDAR, m, ExcelFixture.realWorkdays(),
                            ExcelFixture.vacations(), summary.openingBalanceAt(m), ExcelFixture.TODAY),
                    Map.of()));
        }
        return new ExcelPeriodWriter().write(new ExcelPeriodWriter.PeriodData("Livan Aranda", "IZERTIS",
                "2026-2027", PERIOD_NAME, ExcelFixture.rules(), summary, months));
    }

    private static String signed(int minutes) {
        return (minutes > 0 ? "+" : "") + Minutes.format(minutes);
    }

    @Test
    void theWorkbookHasTheSummarySheetFirstAndOneSheetPerMonth() {
        PeriodSummary summary = periodSummary();
        Map<String, Object> cells = ExcelWorkbookReader.read(export(summary), wb -> {
            Map<String, Object> values = new HashMap<>();
            List<String> names = new ArrayList<>();
            wb.forEach(s -> names.add(s.getSheetName()));
            values.put("names", names);
            Sheet resumen = wb.getSheet(ExcelPeriodWriter.SUMMARY_SHEET);
            values.put("title", resumen.getRow(TITLE_ROW).getCell(0).getStringCellValue());
            Row may = resumen.getRow(FIRST_DATA_ROW);
            values.put("first", may.getCell(0).getStringCellValue());
            values.put("link", may.getCell(0).getHyperlink().getAddress());
            Row october = resumen.getRow(FIRST_DATA_ROW + 5);
            values.put("october", october.getCell(0).getStringCellValue());
            values.put("octoberBalance", october.getCell(11).getStringCellValue());
            Row total = resumen.getRow(FIRST_DATA_ROW + 13);
            values.put("total", total.getCell(0).getStringCellValue());
            values.put("totalWorked", ExcelCells.totalMinutes(total.getCell(8)));
            values.put("julyOpening", openingBalance(wb.getSheet("Julio 2026")));
            return values;
        });

        List<String> expectedNames = new ArrayList<>(List.of(ExcelPeriodWriter.SUMMARY_SHEET));
        for (YearMonth m = YearMonth.of(2026, 5); !m.isAfter(YearMonth.of(2027, 5)); m = m.plusMonths(1)) {
            expectedNames.add(ExcelMonthWriter.sheetName(m));
        }
        MonthRow october = summary.months().stream().filter(r -> r.month().equals(YearMonth.of(2026, 10)))
                .findFirst().orElseThrow();
        assertThat(cells.get("names")).isEqualTo(expectedNames);
        assertThat(cells).containsEntry("title", "Resumen del periodo · 2026-2027")
                .containsEntry("first", "Mayo 2026")
                .containsEntry("link", "'Mayo 2026'!A1")
                .containsEntry("october", "Octubre 2026 (actual)")
                .containsEntry("octoberBalance", signed(october.cumulativeBalanceMinutes()))
                .containsEntry("total", "Total")
                .containsEntry("totalWorked", summary.workedMinutes())
                // Cada mes abre con el saldo acumulado al cerrar el anterior (no con el saldo inicial del periodo).
                .containsEntry("julyOpening", signed(summary.openingBalanceAt(YearMonth.of(2026, 7))));
        assertThat(summary.openingBalanceAt(YearMonth.of(2026, 7))).isNotZero();
    }

    @Test
    void theWholeWorkbookIsReimportedAndTheSummarySheetIsIgnored() {
        ParsedWorkbook parsed = ExcelWorkbookReader.read(export(periodSummary()), new ExcelWorkbookParser()::parse);

        assertThat(parsed.sheets()).hasSize(13).allMatch(ParsedSheet::exported);
        assertThat(parsed.warnings()).isEmpty();
        List<LocalDate> expected = ExcelFixture.realWorkdays().stream().map(w -> w.date()).sorted().toList();
        assertThat(parsed.days()).extracting(ParsedDay::date).containsExactlyElementsOf(expected);
        assertThat(parsed.absences()).extracting(ParsedAbsence::date)
                .containsExactlyElementsOf(ExcelFixture.vacations().stream().map(a -> a.date()).sorted().toList());
        assertThat(parsed.companyMonthDates()).isEmpty();
    }

    /** "Saldo de apertura" del resumen de una hoja mensual. */
    private static String openingBalance(Sheet sheet) {
        for (Row row : sheet) {
            if (row.getCell(COL_BREAKFAST_START) != null
                    && "Saldo de apertura".equals(row.getCell(COL_BREAKFAST_START).toString())) {
                return row.getCell(COL_OTHER_BREAKS).getStringCellValue();
            }
        }
        return null;
    }
}
