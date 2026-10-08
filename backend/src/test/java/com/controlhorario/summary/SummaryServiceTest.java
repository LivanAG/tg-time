package com.controlhorario.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.summary.calc.PeriodSummary;
import com.controlhorario.summary.calc.PeriodSummaryService;

import org.junit.jupiter.api.Test;

class SummaryServiceTest {

    private static WorkPeriod period(String start, String end) {
        WorkPeriod p = new WorkPeriod();
        p.setStartDate(LocalDate.parse(start));
        p.setEndDate(LocalDate.parse(end));
        return p;
    }

    @Test
    void countsTheDaysOfAPeriodInsideAMonth() {
        WorkPeriod excel = period("2026-05-26", "2027-05-25");
        assertThat(SummaryService.daysInMonth(excel, YearMonth.of(2026, 5))).isEqualTo(6);
        assertThat(SummaryService.daysInMonth(excel, YearMonth.of(2026, 6))).isEqualTo(30);
        assertThat(SummaryService.daysInMonth(excel, YearMonth.of(2027, 5))).isEqualTo(25);
        assertThat(SummaryService.daysInMonth(excel, YearMonth.of(2027, 6))).isZero();
        assertThat(SummaryService.daysInMonth(excel, YearMonth.of(2026, 4))).isZero();
    }

    @Test
    void defaultPeriodIsTheOneWithMoreDaysInTheMonthAndTheMostRecentOnATie() {
        WorkPeriod current = period("2026-05-26", "2027-05-25");
        WorkPeriod next = period("2027-05-26", "2028-05-25");
        assertThat(SummaryService.defaultPeriod(List.of(current, next), YearMonth.of(2027, 5))).containsSame(current);
        assertThat(SummaryService.defaultPeriod(List.of(next, current), YearMonth.of(2027, 5))).containsSame(current);
        assertThat(SummaryService.defaultPeriod(List.of(current, next), YearMonth.of(2027, 6))).containsSame(next);

        WorkPeriod firstHalf = period("2030-01-01", "2030-06-15");
        WorkPeriod secondHalf = period("2030-06-16", "2030-12-31");
        assertThat(SummaryService.defaultPeriod(List.of(firstHalf, secondHalf), YearMonth.of(2030, 6)))
                .containsSame(secondHalf);
        assertThat(SummaryService.defaultPeriod(List.of(secondHalf, firstHalf), YearMonth.of(2030, 6)))
                .containsSame(secondHalf);

        assertThat(SummaryService.defaultPeriod(List.of(current), YearMonth.of(2030, 6))).isEmpty();
        assertThat(SummaryService.defaultPeriod(List.of(), YearMonth.of(2030, 6))).isEmpty();
    }

    @Test
    void openingBalanceIsTheBalanceOfThePreviousMonthOrTheInitialOne() {
        PeriodSummary summary = new PeriodSummaryService().summarize(ExcelFixture.calendar(),
                ExcelFixture.realWorkdays(), ExcelFixture.vacations(), ExcelFixture.TODAY);
        assertThat(summary.openingBalanceAt(YearMonth.of(2026, 5))).isZero();
        assertThat(summary.openingBalanceAt(YearMonth.of(2026, 6))).isEqualTo(90);
        assertThat(summary.openingBalanceAt(YearMonth.of(2026, 7))).isEqualTo(210);
    }
}
