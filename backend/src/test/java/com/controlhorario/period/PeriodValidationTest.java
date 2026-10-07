package com.controlhorario.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.controlhorario.common.web.FieldErrorDto;
import com.controlhorario.common.web.ValidationException;

import org.junit.jupiter.api.Test;

class PeriodValidationTest {

    private static final LocalDate START = LocalDate.of(2026, 5, 26);
    private static final LocalDate END = LocalDate.of(2027, 5, 25);

    private static IntensiveRangeDto range(String start, String end) {
        return new IntensiveRangeDto(start == null ? null : LocalDate.parse(start),
                end == null ? null : LocalDate.parse(end));
    }

    private static List<String> errorFields(Runnable validation) {
        try {
            validation.run();
            return List.of();
        } catch (ValidationException e) {
            return e.getErrors().stream().map(FieldErrorDto::field).toList();
        }
    }

    @Test
    void acceptsValidRanges() {
        assertThatCode(() -> IntensiveRangeValidator.validate(null, START, END)).doesNotThrowAnyException();
        assertThatCode(() -> IntensiveRangeValidator.validate(List.of(), START, END)).doesNotThrowAnyException();
        assertThatCode(() -> IntensiveRangeValidator.validate(List.of(
                range("2026-06-15", "2026-09-15"),
                range("2026-05-26", "2026-05-26"),
                range("2027-05-01", "2027-05-25")), START, END)).doesNotThrowAnyException();
    }

    @Test
    void rejectsIncompleteReversedOutsideAndOverlappingRanges() {
        List<IntensiveRangeDto> ranges = new ArrayList<>(Arrays.asList(
                range(null, "2026-09-15"),
                range("2026-09-15", "2026-06-15"),
                range("2026-05-25", "2026-06-01"),
                range("2026-07-01", "2026-07-31"),
                range("2026-07-31", "2026-08-15"),
                null));
        assertThat(errorFields(() -> IntensiveRangeValidator.validate(ranges, START, END))).containsExactly(
                "intensiveRanges[0]", "intensiveRanges[1]", "intensiveRanges[2]", "intensiveRanges[5]",
                "intensiveRanges[4]");
    }

    @Test
    void limitsTheNumberOfRanges() {
        List<IntensiveRangeDto> many = new ArrayList<>();
        for (int i = 0; i <= IntensiveRangeValidator.MAX_RANGES; i++) {
            LocalDate day = START.plusDays(i * 2L);
            many.add(new IntensiveRangeDto(day, day));
        }
        assertThat(errorFields(() -> IntensiveRangeValidator.validate(many, START, END)))
                .containsExactly("intensiveRanges");
    }

    @Test
    void periodDatesMustBeOrderedAndAtMostTwoYearsLong() {
        assertThatCode(() -> PeriodService.validateParameters(params(START, END))).doesNotThrowAnyException();
        assertThatCode(() -> PeriodService.validateParameters(params(START, START.plusYears(2))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PeriodService.validateParameters(params(START, START)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("La fecha de fin debe ser posterior a la de inicio");
        assertThatThrownBy(() -> PeriodService.validateParameters(params(START, START.plusYears(2).plusDays(1))))
                .isInstanceOf(ValidationException.class);
    }

    private static PeriodCreateRequest params(LocalDate start, LocalDate end) {
        return new PeriodCreateRequest("2026-2027", start, end, 105600, 23, 480, 420, 20, 30, 15, 50, 0,
                List.of(), true);
    }
}
