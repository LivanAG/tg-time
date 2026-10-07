package com.controlhorario.summary.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.calendar.calc.PeriodCalendar;

import org.junit.jupiter.api.Test;

class PeriodSummaryServiceTest {

    private final PeriodSummaryService service = new PeriodSummaryService();
    private final PeriodCalendar calendar = ExcelFixture.calendar();

    @Test
    void reproducesTheHorasSheetWithTheRealData() {
        PeriodSummary summary = service.summarize(calendar, ExcelFixture.realWorkdays(), ExcelFixture.vacations(),
                ExcelFixture.TODAY);

        assertThat(summary.workingDays()).isEqualTo(248);
        assertThat(summary.calendarMinutes()).isEqualTo(1917 * 60);
        assertThat(summary.agreementMinutes()).isEqualTo(1760 * 60);
        assertThat(summary.marginMinutes()).isEqualTo(157 * 60);

        VacationSummary vacations = summary.vacations();
        assertThat(vacations.takenDays()).isEqualTo(13);
        assertThat(vacations.takenMinutes()).isEqualTo(91 * 60);
        assertThat(vacations.remainingDays()).isEqualTo(10);
        assertThat(vacations.remainingMinutes()).isEqualTo(80 * 60);
        assertThat(vacations.valueMinutes()).isEqualTo(171 * 60);       // 13 a 7 h + 10 a 8 h
        assertThat(summary.hoursToRecoverMinutes()).isEqualTo(14 * 60);
        assertThat(summary.remainingMarginMinutes()).isEqualTo(66 * 60);

        // Saldo hasta hoy: +1:30 +2:00 +5:01 +0:45 +0:32 (mayo-septiembre) +0:06 (1-7 de octubre).
        assertThat(summary.balanceToDateMinutes()).isEqualTo(9 * 60 + 54);
        assertThat(summary.workedMinutes()).isEqualTo(627 * 60 + 54);
        assertThat(summary.theoreticalRemainingMinutes()).isEqualTo(1208 * 60);
        // 627:54 + 1208:00 - 80:00 (vacaciones sin planificar) - 1760:00
        assertThat(summary.projectionMinutes()).isEqualTo(-(4 * 60 + 6));
        assertThat(summary.months()).hasSize(13);
        assertThat(summary.months().get(1).cumulativeBalanceMinutes()).isEqualTo(90 + 120);
    }

    @Test
    void hoursToRecoverWithAllVacationsAt8Hours() {
        // Ejemplo de la hoja Horas: 23 x 8 h = 184 h frente a un margen de 157 h → 27 h.
        PeriodSummary summary = service.summarize(calendar, List.of(), List.of(), ExcelFixture.TODAY);
        assertThat(summary.vacations().valueMinutes()).isEqualTo(184 * 60);
        assertThat(summary.hoursToRecoverMinutes()).isEqualTo(27 * 60);
        assertThat(summary.remainingMarginMinutes()).isEqualTo(157 * 60);
    }

    @Test
    void hoursToRecoverWithTwelveDaysInTheIntensivePeriod() {
        // Ejemplo de la hoja Horas: 12 días a 7 h + 11 a 8 h = 172 h → 15 h.
        List<AbsenceInput> august = new ArrayList<>();
        for (int day : new int[] {3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 17, 18}) {
            august.add(new AbsenceInput(LocalDate.of(2026, 8, day), AbsenceType.VACACIONES, false));
        }
        PeriodSummary summary = service.summarize(calendar, List.of(), august, LocalDate.of(2026, 6, 1));
        assertThat(summary.vacations().plannedDays()).isEqualTo(12);
        assertThat(summary.vacations().pendingPlannedDays()).isEqualTo(12);
        assertThat(summary.vacations().takenDays()).isZero();
        assertThat(summary.vacations().valueMinutes()).isEqualTo(172 * 60);
        assertThat(summary.hoursToRecoverMinutes()).isEqualTo(15 * 60);
    }

    @Test
    void openingBalanceCarriesIntoBalancesAndProjection() {
        PeriodCalendar withOpening = new PeriodCalendar(ExcelFixture.rules(java.util.Map.of("openingBalanceMin", 300)));
        PeriodSummary base = service.summarize(calendar, ExcelFixture.realWorkdays(), ExcelFixture.vacations(),
                ExcelFixture.TODAY);
        PeriodSummary carried = service.summarize(withOpening, ExcelFixture.realWorkdays(), ExcelFixture.vacations(),
                ExcelFixture.TODAY);
        assertThat(carried.balanceToDateMinutes()).isEqualTo(base.balanceToDateMinutes() + 300);
        assertThat(carried.projectionMinutes()).isEqualTo(base.projectionMinutes() + 300);
        assertThat(carried.months().get(0).cumulativeBalanceMinutes())
                .isEqualTo(base.months().get(0).cumulativeBalanceMinutes() + 300);
    }

    @Test
    void warnsWhenMoreVacationDaysAreMarkedThanTheAgreementAllows() {
        List<AbsenceInput> tooMany = LocalDate.of(2026, 10, 1).datesUntil(LocalDate.of(2026, 12, 1))
                .filter(calendar::isWorkingDay)
                .map(d -> new AbsenceInput(d, AbsenceType.VACACIONES, false))
                .toList();
        PeriodSummary summary = service.summarize(calendar, List.of(), tooMany, ExcelFixture.TODAY);
        assertThat(summary.vacations().unplannedDays()).isZero();
        assertThat(summary.warnings()).extracting(w -> w.code()).containsExactly(PeriodSummaryService.VACATION_OVERPLANNED);
    }
}
