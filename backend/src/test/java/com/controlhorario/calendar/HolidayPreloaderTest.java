package com.controlhorario.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.controlhorario.calendar.calc.DateRange;
import com.controlhorario.calendar.calc.PeriodCalendar;
import com.controlhorario.calendar.calc.PeriodRules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolidayPreloaderTest {

    private final HolidayPreloader preloader = new HolidayPreloader(mock(HolidayRepository.class));

    @ParameterizedTest
    @CsvSource({
            "2019, 2019-04-21",
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28",
            "2028, 2028-04-16",
            "2038, 2038-04-25",
            "2285, 2285-03-22"})
    void computesEasterSunday(int year, LocalDate easter) {
        assertThat(HolidayPreloader.easterSunday(year)).isEqualTo(easter);
    }

    @Test
    void readsTheCsvYears() {
        assertThat(preloader.csvYears()).contains(2026, 2027);
        List<HolidaySeed> year2026 = preloader.holidaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(year2026).hasSize(17);
        assertThat(year2026).contains(
                new HolidaySeed(LocalDate.of(2026, 11, 2), "Lunes siguiente a Todos los Santos", HolidayScope.AUTONOMICO),
                new HolidaySeed(LocalDate.of(2026, 12, 24), "Nochebuena (convenio)", HolidayScope.EMPRESA));
        assertThat(year2026).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
    }

    @Test
    void excelPeriodWithPreloadedHolidaysHas248WorkingDaysAnd1917Hours() {
        LocalDate start = LocalDate.of(2026, 5, 26);
        LocalDate end = LocalDate.of(2027, 5, 25);
        Map<LocalDate, String> holidays = new LinkedHashMap<>();
        preloader.holidaysBetween(start, end).forEach(h -> holidays.put(h.date(), h.name()));
        PeriodCalendar calendar = new PeriodCalendar(new PeriodRules(start, end, 105600, 23, 480, 420, 20, 30, 15,
                50, 0, List.of(new DateRange(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 9, 15))), holidays));

        List<LocalDate> all = start.datesUntil(end.plusDays(1)).toList();
        assertThat(all.stream().filter(calendar::isWorkingDay)).hasSize(248);
        assertThat(all.stream().mapToInt(calendar::dayMinutes).sum()).isEqualTo(1917 * 60);
        assertThat(PeriodCalendar.datesOf(YearMonth.of(2026, 12)).stream().filter(calendar::isWorkingDay))
                .hasSize(18);
    }

    @Test
    void fallbackCoversYearsMissingFromTheCsv() {
        List<HolidaySeed> year2028 = HolidayPreloader.fallback(2028);
        assertThat(year2028).hasSize(16);
        assertThat(year2028).contains(
                new HolidaySeed(LocalDate.of(2028, 4, 13), "Jueves Santo", HolidayScope.AUTONOMICO),
                new HolidaySeed(LocalDate.of(2028, 4, 14), "Viernes Santo", HolidayScope.NACIONAL),
                new HolidaySeed(LocalDate.of(2028, 5, 2), "Fiesta de la Comunidad de Madrid", HolidayScope.AUTONOMICO),
                new HolidaySeed(LocalDate.of(2028, 5, 15), "San Isidro", HolidayScope.LOCAL),
                new HolidaySeed(LocalDate.of(2028, 11, 9), "Nuestra Señora de la Almudena", HolidayScope.LOCAL),
                new HolidaySeed(LocalDate.of(2028, 12, 31), "Nochevieja (convenio)", HolidayScope.EMPRESA));
        assertThat(year2028).extracting(HolidaySeed::scope)
                .filteredOn(s -> s == HolidayScope.NACIONAL).hasSize(10);

        // Un periodo que cruza 2027 (CSV) y 2028 (cálculo) toma cada año de su fuente.
        List<HolidaySeed> period = preloader.holidaysBetween(LocalDate.of(2027, 5, 26), LocalDate.of(2028, 5, 25));
        assertThat(period).extracting(HolidaySeed::date)
                .contains(LocalDate.of(2027, 12, 6), LocalDate.of(2028, 4, 14), LocalDate.of(2028, 5, 15))
                .doesNotContain(LocalDate.of(2027, 5, 15), LocalDate.of(2028, 8, 15));
        assertThat(period.get(0).date()).isEqualTo(LocalDate.of(2027, 8, 15));
    }

    @Test
    void parsesTheCsvFormat() {
        Map<Integer, List<HolidaySeed>> parsed = HolidayPreloader.parseCsv(List.of(
                "# comentario",
                "",
                "2030-01-01, Año Nuevo ,NACIONAL",
                "2030-03-19,San José, AUTONOMICO"));
        assertThat(parsed.get(2030)).containsExactly(
                new HolidaySeed(LocalDate.of(2030, 1, 1), "Año Nuevo", HolidayScope.NACIONAL),
                new HolidaySeed(LocalDate.of(2030, 3, 19), "San José", HolidayScope.AUTONOMICO));

        assertThatThrownBy(() -> HolidayPreloader.parseCsv(List.of("2030-01-01,Año Nuevo,REGIONAL")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 1");
        assertThatThrownBy(() -> HolidayPreloader.parseCsv(List.of("2030-02-30,Imposible,NACIONAL")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HolidayPreloader.parseCsv(List.of("2030-01-01")))
                .isInstanceOf(IllegalStateException.class);
    }
}
