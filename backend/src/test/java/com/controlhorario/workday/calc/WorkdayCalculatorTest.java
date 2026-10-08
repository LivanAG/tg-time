package com.controlhorario.workday.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorkdayCalculatorTest {

    private final WorkdayCalculator calculator = new WorkdayCalculator(20, 30);

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    private static WorkdayInput day(String start, String end, BreakInput... breaks) {
        return new WorkdayInput(LocalDate.of(2026, 5, 26), t(start), t(end), List.of(breaks), Location.OFICINA, null);
    }

    private static BreakInput br(BreakType type, String start, String end) {
        return new BreakInput(type, t(start), t(end));
    }

    @Test
    void referenceDayOfTheSpecificationGives10h04() {
        // 26/05/2026: 07:25-17:59, desayuno de 19 min (dentro de la tolerancia) y comida de 30 min.
        WorkdayResult result = calculator.calculate(day("07:25", "17:59",
                br(BreakType.DESAYUNO, "12:43", "13:02"), br(BreakType.COMIDA, "15:02", "15:32")));

        assertThat(result.grossMinutes()).isEqualTo(634);
        assertThat(result.breakfastDeductedMinutes()).isZero();
        assertThat(result.lunchDeductedMinutes()).isEqualTo(30);
        assertThat(result.workedMinutes()).isEqualTo(10 * 60 + 4);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void breakfastOverToleranceDeductsOnlyTheExcess() {
        // Con 20 min de tolerancia, un desayuno de 25 min descuenta 5 (columna K del Excel).
        WorkdayResult result = calculator.calculate(day("08:00", "16:00", br(BreakType.DESAYUNO, "10:00", "10:25")));

        assertThat(result.breakfastMinutes()).isEqualTo(25);
        assertThat(result.breakfastDeductedMinutes()).isEqualTo(5);
        assertThat(result.workedMinutes()).isEqualTo(475);
    }

    @Test
    void shortLunchDeductsTheMinimumAndWarns() {
        // 09/06/2026 del Excel: comida 15:53-16:19 (26 min) → se descuentan 30.
        WorkdayResult result = calculator.calculate(day("07:49", "17:35",
                br(BreakType.DESAYUNO, "13:09", "13:28"), br(BreakType.COMIDA, "15:53", "16:19")));

        assertThat(result.lunchMinutes()).isEqualTo(26);
        assertThat(result.lunchDeductedMinutes()).isEqualTo(30);
        assertThat(result.workedMinutes()).isEqualTo(9 * 60 + 16);
        assertThat(result.warnings()).extracting(CalcIssue::code).containsExactly(WorkdayCalculator.LUNCH_BELOW_MINIMUM);
    }

    @Test
    void longLunchDeductsItsRealDuration() {
        WorkdayResult result = calculator.calculate(day("07:28", "17:45", br(BreakType.COMIDA, "15:27", "15:59")));
        assertThat(result.lunchDeductedMinutes()).isEqualTo(32);
    }

    @Test
    void withoutLunchNoMinimumIsApplied() {
        WorkdayResult result = calculator.calculate(day("08:00", "15:00"));
        assertThat(result.lunchDeductedMinutes()).isZero();
        assertThat(result.workedMinutes()).isEqualTo(420);
    }

    @Test
    void otherBreaksAreFullyDeducted() {
        WorkdayResult result = calculator.calculate(day("08:00", "16:00",
                br(BreakType.OTRA, "11:00", "11:10"), br(BreakType.OTRA, "12:00", "12:05")));
        assertThat(result.otherBreakMinutes()).isEqualTo(15);
        assertThat(result.workedMinutes()).isEqualTo(465);
    }

    private static WorkdayInput mixed(String officeStart, String officeEnd, String homeStart, String homeEnd,
            BreakInput... breaks) {
        return new WorkdayInput(LocalDate.of(2026, 5, 26), null, null, List.of(breaks), Location.MIXTO,
                new MixedTimes(t(officeStart), t(officeEnd), t(homeStart), t(homeEnd)));
    }

    @Test
    void locationSplitsOfficeAndRemoteMinutes() {
        WorkdayInput base = day("08:00", "16:00");
        WorkdayInput office = base;
        WorkdayInput home = new WorkdayInput(base.date(), base.start(), base.end(), List.of(), Location.CASA, null);
        WorkdayInput mixed = mixed("08:00", "13:00", "13:00", "16:00");

        assertThat(calculator.calculate(office)).extracting(WorkdayResult::officeMinutes, WorkdayResult::remoteMinutes)
                .containsExactly(480, 0);
        assertThat(calculator.calculate(home)).extracting(WorkdayResult::officeMinutes, WorkdayResult::remoteMinutes)
                .containsExactly(0, 480);
        assertThat(calculator.calculate(mixed)).extracting(WorkdayResult::officeMinutes, WorkdayResult::remoteMinutes)
                .containsExactly(300, 180);
    }

    @Test
    void mixedDayCountsBothSegmentsButNotTheGapBetweenThem() {
        // Oficina 07:30-14:00 con desayuno de 30 min (10 por encima de la tolerancia); casa 15:00-18:00 con
        // comida de 20 min (se descuenta el mínimo de 30). El hueco 14:00-15:00 no cuenta.
        WorkdayInput day = mixed("07:30", "14:00", "15:00", "18:00",
                br(BreakType.DESAYUNO, "10:00", "10:30"), br(BreakType.COMIDA, "16:00", "16:20"));
        assertThat(day.start()).isEqualTo(t("07:30"));
        assertThat(day.end()).isEqualTo(t("18:00"));
        assertThat(calculator.validate(day)).isEmpty();

        WorkdayResult result = calculator.calculate(day);
        assertThat(result.grossMinutes()).isEqualTo(390 + 180);
        assertThat(result.workedMinutes()).isEqualTo(570 - 10 - 30);
        // Lo que se descuenta de cada pausa va a su tramo: casa 180 - 30, oficina 390 - 10.
        assertThat(result.remoteMinutes()).isEqualTo(150);
        assertThat(result.officeMinutes()).isEqualTo(380);

        // En casa por la mañana y en la oficina por la tarde, también con hueco.
        WorkdayInput homeFirst = mixed("10:00", "15:00", "07:00", "09:00");
        assertThat(homeFirst.start()).isEqualTo(t("07:00"));
        assertThat(homeFirst.end()).isEqualTo(t("15:00"));
        assertThat(calculator.calculate(homeFirst)).extracting(WorkdayResult::grossMinutes,
                WorkdayResult::officeMinutes, WorkdayResult::remoteMinutes).containsExactly(420, 300, 120);
    }

    @Test
    void validatesTimesBreaksAndMixedSegments() {
        assertThat(codes(day("16:00", "08:00"))).containsExactly(WorkdayCalculator.END_BEFORE_START);
        assertThat(codes(day("08:00", "16:00", br(BreakType.OTRA, "07:30", "08:15"))))
                .containsExactly(WorkdayCalculator.BREAK_OUTSIDE_WORKDAY);
        assertThat(codes(day("08:00", "16:00", br(BreakType.OTRA, "10:00", "10:30"), br(BreakType.COMIDA, "10:15", "11:00"))))
                .containsExactly(WorkdayCalculator.BREAKS_OVERLAP);
        assertThat(codes(day("08:00", "16:00", br(BreakType.DESAYUNO, "10:00", "10:10"), br(BreakType.DESAYUNO, "11:00", "11:10"))))
                .containsExactly(WorkdayCalculator.DUPLICATE_BREAKFAST);
        assertThat(codes(day("08:00", "18:00", br(BreakType.COMIDA, "13:00", "13:30"), br(BreakType.COMIDA, "14:00", "14:30"))))
                .containsExactly(WorkdayCalculator.DUPLICATE_LUNCH);
        assertThat(codes(day("08:00", "16:00", br(BreakType.OTRA, "10:00", "10:00"))))
                .containsExactly(WorkdayCalculator.BREAK_INVALID);

        WorkdayInput mixedWithout = new WorkdayInput(LocalDate.of(2026, 6, 1), t("08:00"), t("16:00"), List.of(),
                Location.MIXTO, null);
        assertThat(calculator.validate(mixedWithout)).extracting(CalcIssue::field).containsExactly("officeStart",
                "homeStart");
        assertThat(calculator.validate(mixed("08:00", "13:00", "16:00", "15:00"))).extracting(CalcIssue::field)
                .containsExactly("homeEnd");
        assertThat(codes(mixed("08:00", "13:00", "12:00", "16:00"))).containsExactly(WorkdayCalculator.SEGMENTS_OVERLAP);
        // Una pausa en el hueco entre los tramos no vale: el hueco ya no se trabaja.
        assertThat(codes(mixed("08:00", "13:00", "14:00", "17:00", br(BreakType.OTRA, "13:15", "13:30"))))
                .containsExactly(WorkdayCalculator.BREAK_OUTSIDE_WORKDAY);
        assertThat(codes(day("07:25", "17:59", br(BreakType.DESAYUNO, "12:43", "13:02")))).isEmpty();
    }

    private List<String> codes(WorkdayInput input) {
        return calculator.validate(input).stream().map(CalcIssue::code).toList();
    }

    static Stream<Arguments> excelDays() {
        return ExcelFixture.workedRows(ExcelFixture.Row::representable).stream()
                .map(r -> Arguments.of(r.sheet() + " fila " + r.row() + " (" + r.date() + ")", r));
    }

    /** Cada día del Excel debe dar exactamente su "Total Día" (columna L). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("excelDays")
    void reproducesEveryDayOfTheExcel(String name, ExcelFixture.Row row) {
        WorkdayInput input = row.toWorkdayInput();
        assertThat(calculator.validate(input)).isEmpty();
        assertThat(calculator.calculate(input).workedMinutes()).isEqualTo(row.excelWorkedMinutes());
    }

    @Test
    void theFixtureCoversTheWholeExcel() {
        assertThat(ExcelFixture.workedRows(r -> true)).hasSize(235);
        // Los únicos días que no se pueden representar son planes futuros con la comida sin horas.
        assertThat(ExcelFixture.workedRows(r -> !r.representable()))
                .hasSize(13)
                .allMatch(r -> r.date().isAfter(ExcelFixture.TODAY));
    }
}
