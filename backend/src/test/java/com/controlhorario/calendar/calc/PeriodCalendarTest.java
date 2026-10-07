package com.controlhorario.calendar.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.controlhorario.calc.ExcelFixture;

import org.junit.jupiter.api.Test;

class PeriodCalendarTest {

    private final PeriodCalendar calendar = ExcelFixture.calendar();

    @Test
    void periodHas248WorkingDaysAnd1917Hours() {
        List<LocalDate> all = LocalDate.of(2026, 5, 26).datesUntil(LocalDate.of(2027, 5, 26)).toList();
        assertThat(all.stream().filter(calendar::isWorkingDay)).hasSize(248);
        assertThat(all.stream().mapToInt(calendar::dayMinutes).sum()).isEqualTo(1917 * 60);
    }

    @Test
    void workingDaysPerMonthMatchTheHorasSheet() {
        // Hoja Horas, filas 13-25: días a 8 h + días a 7 h de cada mes.
        int[] expected = {4, 22, 23, 21, 22, 21, 19, 18, 19, 20, 20, 22, 17};
        List<YearMonth> months = calendar.months();
        assertThat(months).hasSize(13);
        for (int i = 0; i < months.size(); i++) {
            long days = PeriodCalendar.datesOf(months.get(i)).stream().filter(calendar::isWorkingDay).count();
            assertThat(days).as(months.get(i).toString()).isEqualTo(expected[i]);
        }
    }

    @Test
    void intensiveRangeMakesJuneAndSeptemberMixed() {
        assertThat(calendar.dayMinutes(LocalDate.of(2026, 6, 12))).isEqualTo(480);
        assertThat(calendar.dayMinutes(LocalDate.of(2026, 6, 15))).isEqualTo(420);
        assertThat(calendar.dayMinutes(LocalDate.of(2026, 9, 15))).isEqualTo(420);
        assertThat(calendar.dayMinutes(LocalDate.of(2026, 9, 16))).isEqualTo(480);
        long juneAt7 = PeriodCalendar.datesOf(YearMonth.of(2026, 6)).stream()
                .filter(d -> calendar.dayMinutes(d) == 420).count();
        long septemberAt7 = PeriodCalendar.datesOf(YearMonth.of(2026, 9)).stream()
                .filter(d -> calendar.dayMinutes(d) == 420).count();
        assertThat(juneAt7).isEqualTo(12);
        assertThat(septemberAt7).isEqualTo(11);
    }

    @Test
    void classifiesDays() {
        assertThat(calendar.dayType(LocalDate.of(2026, 5, 25))).isEqualTo(DayType.FUERA_DE_PERIODO);
        assertThat(calendar.dayType(LocalDate.of(2026, 5, 26))).isEqualTo(DayType.LABORABLE);
        assertThat(calendar.dayType(LocalDate.of(2026, 5, 30))).isEqualTo(DayType.FIN_DE_SEMANA);
        assertThat(calendar.dayType(LocalDate.of(2026, 10, 12))).isEqualTo(DayType.FESTIVO);
        assertThat(calendar.holidayName(LocalDate.of(2026, 10, 12))).contains("Fiesta Nacional de España");
        assertThat(calendar.dayMinutes(LocalDate.of(2026, 10, 12))).isZero();
        assertThat(calendar.dayType(LocalDate.of(2027, 5, 26))).isEqualTo(DayType.FUERA_DE_PERIODO);
    }
}
