package com.controlhorario.summary.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class RoundingServiceTest {

    private final RoundingService rounding = new RoundingService(15);

    @Test
    void roundsToTheNearestQuarterHour() {
        assertThat(rounding.round(0)).isZero();
        assertThat(rounding.round(7)).isZero();
        assertThat(rounding.round(8)).isEqualTo(15);
        assertThat(rounding.round(7 * 60 + 46)).isEqualTo(7 * 60 + 45);
        assertThat(rounding.round(8 * 60 + 9)).isEqualTo(8 * 60 + 15);
        assertThat(rounding.round(166 * 60)).isEqualTo(166 * 60);
        assertThat(rounding.round(159 * 60 + 1)).isEqualTo(159 * 60);
    }

    @Test
    void tiesRoundUp() {
        RoundingService tens = new RoundingService(10);
        assertThat(tens.round(5)).isEqualTo(10);
        assertThat(tens.round(15)).isEqualTo(20);
        assertThat(tens.round(14)).isEqualTo(10);
    }

    @Test
    void distributesTheRoundedAccumulatedTotal() {
        // Semanas 1 y 2 de junio del Excel: 39:16 y 39:57 → 39:15 y 40:00 (columna P del Excel).
        List<Integer> week1 = List.of(466, 489, 437, 538, 426);
        List<Integer> week2 = List.of(458, 556, 405, 477, 501);
        List<Integer> all = new ArrayList<>(week1);
        all.addAll(week2);

        List<Integer> rounded = rounding.distribute(all);

        assertThat(rounded.subList(0, 5).stream().mapToInt(Integer::intValue).sum()).isEqualTo(39 * 60 + 15);
        assertThat(rounded.subList(5, 10).stream().mapToInt(Integer::intValue).sum()).isEqualTo(40 * 60);
        assertThat(rounded).allMatch(r -> r % 15 == 0);
    }

    @Test
    void theMonthNeverDriftsMoreThanHalfAStep() {
        Random random = new Random(42);
        for (int month = 0; month < 2_000; month++) {
            List<Integer> days = new ArrayList<>();
            for (int d = 0; d < 23; d++) {
                days.add(random.nextInt(4 * 60, 11 * 60));
            }
            int real = days.stream().mapToInt(Integer::intValue).sum();
            int rounded = rounding.distribute(days).stream().mapToInt(Integer::intValue).sum();
            assertThat(Math.abs(rounded - real)).isLessThanOrEqualTo(7);
            assertThat(rounded).isEqualTo(rounding.round(real));
        }
    }
}
