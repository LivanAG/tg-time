package com.controlhorario.summary.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.WorkdayInput;

import org.junit.jupiter.api.Test;

class MonthSummaryServiceTest {

    private final MonthSummaryService service = new MonthSummaryService();
    private final PeriodCalendar calendar = ExcelFixture.calendar();

    private MonthSummary month(int year, int month) {
        return service.summarize(calendar, YearMonth.of(year, month), ExcelFixture.realWorkdays(),
                ExcelFixture.vacations(), 0, ExcelFixture.TODAY);
    }

    @Test
    void juneMatchesTheExcel() {
        MonthSummary june = month(2026, 6);

        assertThat(june.workingDays()).isEqualTo(22);
        assertThat(june.normalDays()).isEqualTo(10);
        assertThat(june.intensiveDays()).isEqualTo(12);
        assertThat(june.theoreticalMinutes()).isEqualTo(164 * 60);   // C46 = 10x8 + 12x7
        assertThat(june.workedMinutes()).isEqualTo(166 * 60);        // C47
        assertThat(june.differenceMinutes()).isEqualTo(120);         // sobran 2:00
        assertThat(june.roundedMinutes()).isEqualTo(166 * 60);       // P43
        assertThat(june.status()).isEqualTo(MonthStatus.PAST);
        // Semanas 1 y 2: L12/P12 y L19/P19 del Excel.
        assertThat(june.weeks().get(0)).extracting(WeekSummary::workedMinutes, WeekSummary::roundedMinutes)
                .containsExactly(39 * 60 + 16, 39 * 60 + 15);
        assertThat(june.weeks().get(1)).extracting(WeekSummary::workedMinutes, WeekSummary::roundedMinutes)
                .containsExactly(39 * 60 + 57, 40 * 60);
    }

    @Test
    void monthlyDifferencesMatchTheHorasSheetSignCorrected() {
        // Fila "Horas Sobrantes" de la hoja Horas (el Excel las da siempre en positivo).
        assertThat(month(2026, 5).differenceMinutes()).isEqualTo(90);
        assertThat(month(2026, 7).differenceMinutes()).isEqualTo(5 * 60 + 1);
        assertThat(month(2026, 8).differenceMinutes()).isEqualTo(45);
        assertThat(month(2026, 9).differenceMinutes()).isEqualTo(32);
    }

    @Test
    void vacationsReduceTheTheoreticalHours() {
        MonthSummary july = month(2026, 7);
        assertThat(july.theoreticalMinutes()).isEqualTo(154 * 60);  // 22 x 7 (23 laborables - 1 vacación)
        assertThat(july.vacationDays()).isEqualTo(1);
        assertThat(july.vacationMinutes()).isEqualTo(7 * 60);
        assertThat(july.workedMinutes()).isEqualTo(159 * 60 + 1);

        MonthSummary august = month(2026, 8);
        assertThat(august.theoreticalMinutes()).isEqualTo(63 * 60);  // 9 x 7
        assertThat(august.vacationDays()).isEqualTo(12);
        assertThat(august.vacationMinutes()).isEqualTo(84 * 60);
        assertThat(august.workedMinutes()).isEqualTo(63 * 60 + 45);
    }

    @Test
    void roundedDaysAddUpToTheRoundedMonth() {
        for (int m = 6; m <= 9; m++) {
            MonthSummary summary = month(2026, m);
            int sum = summary.days().stream().mapToInt(DaySummary::roundedMinutes).sum();
            assertThat(sum).isEqualTo(summary.roundedMinutes());
            assertThat(Math.abs(sum - summary.workedMinutes())).isLessThanOrEqualTo(7);
        }
    }

    @Test
    void remoteWorkIsMeasuredAgainstTheLimits() {
        MonthSummary june = month(2026, 6);
        int homeMinutes = june.days().stream()
                .filter(d -> d.workday() != null && d.workday().location() == Location.CASA)
                .mapToInt(DaySummary::workedMinutes).sum();
        assertThat(june.remoteMinutes()).isEqualTo(homeMinutes);
        assertThat(june.officeMinutes()).isEqualTo(june.workedMinutes() - homeMinutes);
        assertThat(june.remoteDays()).isEqualTo(10);
        // 10 días en casa no generan aviso: el límite es solo el porcentaje.
        assertThat(june.remotePct()).isLessThanOrEqualTo(50);
        assertThat(june.warnings()).isEmpty();
    }

    @Test
    void countdownGoesFromTheoreticalToTheDifference() {
        MonthSummary july = month(2026, 7);
        DaySummary last = july.days().get(july.days().size() - 1);
        assertThat(last.countdownMinutes()).isEqualTo(-july.differenceMinutes());
        assertThat(july.days().get(0).countdownMinutes())
                .isEqualTo(july.theoreticalMinutes() - july.days().get(0).workedMinutes());
    }

    @Test
    void currentMonthReportsTheBalanceToDate() {
        MonthSummary october = month(2026, 10);
        assertThat(october.status()).isEqualTo(MonthStatus.CURRENT);
        assertThat(october.theoreticalMinutes()).isEqualTo(21 * 8 * 60);
        assertThat(october.theoreticalToDateMinutes()).isEqualTo(5 * 8 * 60);   // 1, 2, 5, 6 y 7 de octubre
        assertThat(october.workedToDateMinutes()).isEqualTo(40 * 60 + 6);
        assertThat(october.differenceToDateMinutes()).isEqualTo(6);
    }

    @Test
    void flagsPastWorkingDaysWithoutRecordOrAbsence() {
        // Sin las vacaciones, el 10/07 queda como laborable sin fichaje.
        MonthSummary july = service.summarize(calendar, YearMonth.of(2026, 7), ExcelFixture.realWorkdays(), List.of(),
                0, ExcelFixture.TODAY);
        DaySummary tenth = july.days().get(9);
        assertThat(tenth.date()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(tenth.warnings()).extracting(CalcIssue::code).containsExactly(MonthSummaryService.MISSING_RECORD);
        // Los días futuros no se marcan.
        MonthSummary october = month(2026, 10);
        assertThat(october.days().get(20).warnings()).isEmpty();
    }

    @Test
    void remotePercentageAboveTheLimitIsWarned() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        WorkdayInput home = new WorkdayInput(date, LocalTime.of(8, 0), LocalTime.of(15, 0), List.of(),
                Location.CASA, null);
        MonthSummary june = service.summarize(calendar, YearMonth.of(2026, 6), List.of(home), List.of(), 0,
                ExcelFixture.TODAY);
        assertThat(june.remotePct()).isEqualTo(100.0);
        assertThat(june.warnings()).extracting(CalcIssue::code).containsExactly(MonthSummaryService.REMOTE_PCT_EXCEEDED);
    }

    @Test
    void halfDayAbsencesCountHalfAndBridgesReduceTheBalance() {
        List<AbsenceInput> absences = List.of(
                new AbsenceInput(LocalDate.of(2026, 10, 13), AbsenceType.VACACIONES, true),
                new AbsenceInput(LocalDate.of(2026, 10, 14), AbsenceType.PUENTE, false),
                new AbsenceInput(LocalDate.of(2026, 10, 17), AbsenceType.VACACIONES, false));   // sábado: no cuenta
        MonthSummary october = service.summarize(calendar, YearMonth.of(2026, 10), List.of(), absences, 100,
                LocalDate.of(2026, 10, 31));

        assertThat(october.vacationDays()).isEqualTo(0.5);
        assertThat(october.vacationMinutes()).isEqualTo(240);
        assertThat(october.bridgeDays()).isEqualTo(1);
        assertThat(october.bridgeMinutes()).isEqualTo(480);
        assertThat(october.theoreticalMinutes()).isEqualTo(21 * 480 - 240 - 480);
        assertThat(october.closingBalanceMinutes())
                .isEqualTo(100 + october.differenceMinutes() - 480);
    }

    @Test
    void daysOutsideThePeriodDoNotCount() {
        MonthSummary may = month(2026, 5);
        assertThat(may.workingDays()).isEqualTo(4);
        assertThat(may.theoreticalMinutes()).isEqualTo(32 * 60);
        assertThat(may.workedMinutes()).isEqualTo(33 * 60 + 30);
        assertThat(may.days()).hasSize(31);
    }
}
