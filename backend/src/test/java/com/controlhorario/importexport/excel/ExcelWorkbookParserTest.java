package com.controlhorario.importexport.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Lectura del Excel real HORAS_IZERTIS_2026-27 comparada con su interpretación de referencia (days.csv). */
class ExcelWorkbookParserTest {

    private static ParsedWorkbook parsed;

    @BeforeAll
    static void parseRealExcel() {
        parsed = ExcelWorkbookReader.read(TestWorkbooks.realExcel(), new ExcelWorkbookParser()::parse);
    }

    @Test
    void detectsTheThirteenMonthlySheetsByTheDatesOfColumnA() {
        assertThat(parsed.sheets()).extracting(ParsedSheet::name, ParsedSheet::month).containsExactly(
                tuple("Mayo 26", YearMonth.of(2026, 5)),
                tuple("Junio", YearMonth.of(2026, 6)),
                tuple("Julio", YearMonth.of(2026, 7)),
                tuple("Agosto", YearMonth.of(2026, 8)),
                tuple("Septiembre", YearMonth.of(2026, 9)),
                tuple("Octubre", YearMonth.of(2026, 10)),
                tuple("Noviembre", YearMonth.of(2026, 11)),
                tuple("Diciembre", YearMonth.of(2026, 12)),
                tuple("Enero", YearMonth.of(2027, 1)),
                tuple("Febrero", YearMonth.of(2027, 2)),
                tuple("Marzo", YearMonth.of(2027, 3)),
                tuple("Abril", YearMonth.of(2027, 4)),
                // B3 dice 2026-05-01, pero las fechas de la columna A son de 2027.
                tuple("Mayo", YearMonth.of(2027, 5)));
    }

    @Test
    void countsTheRowsOfEachMonthLikeTheReference() {
        Map<String, Long> expected = ExcelFixture.rows().stream()
                .collect(Collectors.groupingBy(ExcelFixture.Row::sheet, Collectors.counting()));
        for (ParsedSheet sheet : parsed.sheets()) {
            assertThat((long) sheet.rows()).as(sheet.name()).isEqualTo(expected.get(sheet.name()));
        }
        assertThat(parsed.sheets()).filteredOn(s -> s.name().equals("Junio")).extracting(ParsedSheet::rows)
                .containsExactly(22);
    }

    @Test
    void correctsTheTenWrongDatesOfColumnAByPosition() {
        assertThat(parsed.sheets().stream().mapToInt(ParsedSheet::dateCorrections).sum()).isEqualTo(10);
        assertThat(parsed.sheets()).filteredOn(s -> s.dateCorrections() > 0)
                .extracting(ParsedSheet::name, ParsedSheet::dateCorrections)
                .containsExactly(tuple("Mayo 26", 5), tuple("Agosto", 5));
        assertThat(parsed.warnings()).hasSize(2);
        assertThat(parsed.warnings().get(0)).startsWith("Mayo 26: 5 filas").contains("filas 21-25");
        assertThat(parsed.warnings().get(1)).startsWith("Agosto: 5 filas").contains("filas 21-25");
    }

    @Test
    void readsEveryWorkedDayLikeTheReference() {
        Map<LocalDate, ExcelFixture.Row> expected = ExcelFixture.workedRows(r -> true).stream()
                .collect(Collectors.toMap(ExcelFixture.Row::date, Function.identity()));
        List<ParsedDay> days = parsed.days();

        assertThat(days).hasSize(235);
        assertThat(days).extracting(ParsedDay::date).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (ParsedDay day : days) {
            ExcelFixture.Row row = expected.get(day.date());
            WorkdayInput reference = row.toWorkdayInput();
            String name = day.sheet() + " fila " + day.row() + " (" + day.date() + ")";
            assertThat(day.sheet()).as(name).isEqualTo(row.sheet());
            assertThat(day.row()).as(name).isEqualTo(row.row());
            assertThat(day.startTime()).as(name).isEqualTo(reference.start());
            assertThat(day.endTime()).as(name).isEqualTo(reference.end());
            assertThat(day.breaks()).as(name).containsExactlyInAnyOrderElementsOf(reference.breaks());
            assertThat(day.location()).as(name).isEqualTo(reference.location());
            assertThat(day.remoteMinutes()).as(name).isNull();
            assertThat(day.excelWorkedMinutes()).as(name).isEqualTo(row.excelWorkedMinutes());
            assertThat(day.representable()).as(name).isEqualTo(row.representable());
            assertThat(day.errors()).as(name).isEmpty();
        }
    }

    @Test
    void everyRepresentableDayGivesExactlyTheTotalOfTheExcel() {
        WorkdayCalculator calculator = new WorkdayCalculator(20, 30);
        List<ParsedDay> representable = parsed.days().stream().filter(ParsedDay::representable).toList();

        assertThat(representable).hasSize(222);
        for (ParsedDay day : representable) {
            WorkdayInput input = new WorkdayInput(day.date(), day.startTime(), day.endTime(), day.breaks(),
                    day.location(), day.remoteMinutes());
            assertThat(calculator.validate(input)).as(day.date().toString()).isEmpty();
            assertThat(calculator.calculate(input).workedMinutes()).as(day.date().toString())
                    .isEqualTo(day.excelWorkedMinutes());
        }
    }

    @Test
    void handwrittenLunchWithoutTimesIsOnlyRepresentableWhenTheBreakStartsAfterTwo() {
        List<ParsedDay> notRepresentable = parsed.days().stream().filter(d -> !d.representable()).toList();
        assertThat(notRepresentable).hasSize(13).allMatch(d -> d.date().isAfter(ExcelFixture.TODAY));
        assertThat(notRepresentable).allSatisfy(d -> assertThat(d.messages()).anyMatch(m -> m.contains("a mano")));

        // 29/09/2026: J = 0:30 sin E/F y el "desayuno" 15:52-16:02 era la comida.
        ParsedDay day = parsed.days().stream().filter(d -> d.date().equals(LocalDate.of(2026, 9, 29))).findFirst()
                .orElseThrow();
        assertThat(day.representable()).isTrue();
        assertThat(day.breaks()).containsExactly(new BreakInput(BreakType.COMIDA, LocalTime.of(15, 52),
                LocalTime.of(16, 2)));
        assertThat(day.messages()).anyMatch(m -> m.contains("se importa como COMIDA"));
    }

    @Test
    void detectsThePeriodSettingsFromTheHeadersAndTheHorasSheet() {
        assertThat(parsed.settings()).isEqualTo(new DetectedSettings(20, 30, 50, 480, 420, 23, 105_600));
    }
}
