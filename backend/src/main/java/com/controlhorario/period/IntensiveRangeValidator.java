package com.controlhorario.period;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.controlhorario.common.web.FieldErrorDto;
import com.controlhorario.common.web.ValidationException;

/**
 * Reglas de los rangos de intensiva: cada rango con inicio y fin (fin ≥ inicio), dentro del
 * periodo y sin solaparse con los demás. Los errores se devuelven en {@code intensiveRanges[i]}.
 */
final class IntensiveRangeValidator {

    static final int MAX_RANGES = 24;

    private IntensiveRangeValidator() {
    }

    static void validate(List<IntensiveRangeDto> ranges, LocalDate periodStart, LocalDate periodEnd) {
        if (ranges == null || ranges.isEmpty()) {
            return;
        }
        List<FieldErrorDto> errors = new ArrayList<>();
        if (ranges.size() > MAX_RANGES) {
            throw new ValidationException("intensiveRanges",
                    "Como máximo puede haber " + MAX_RANGES + " rangos de intensiva");
        }
        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            IntensiveRangeDto range = ranges.get(i);
            String field = field(i);
            if (range == null || range.startDate() == null || range.endDate() == null) {
                errors.add(new FieldErrorDto(field, "El rango necesita fecha de inicio y de fin"));
                continue;
            }
            if (range.endDate().isBefore(range.startDate())) {
                errors.add(new FieldErrorDto(field, "El fin del rango no puede ser anterior a su inicio"));
                continue;
            }
            if (range.startDate().isBefore(periodStart) || range.endDate().isAfter(periodEnd)) {
                errors.add(new FieldErrorDto(field, "El rango de intensiva debe estar dentro del periodo"));
                continue;
            }
            valid.add(i);
        }
        valid.sort(Comparator.comparing(i -> ranges.get(i).startDate()));
        for (int k = 1; k < valid.size(); k++) {
            IntensiveRangeDto previous = ranges.get(valid.get(k - 1));
            IntensiveRangeDto current = ranges.get(valid.get(k));
            if (!current.startDate().isAfter(previous.endDate())) {
                errors.add(new FieldErrorDto(field(valid.get(k)), "Los rangos de intensiva no pueden solaparse"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static String field(int index) {
        return "intensiveRanges[" + index + "]";
    }
}
