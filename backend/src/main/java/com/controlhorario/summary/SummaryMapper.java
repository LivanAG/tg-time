package com.controlhorario.summary;

import java.time.YearMonth;
import java.util.List;

import com.controlhorario.summary.calc.MonthRow;
import com.controlhorario.summary.calc.VacationSummary;
import com.controlhorario.summary.calc.WeekSummary;

import org.mapstruct.Mapper;

/** Registros del cálculo → DTOs de los resúmenes (las piezas simples; el ensamblaje está en SummaryService). */
@Mapper
public interface SummaryMapper {

    WeekDto toDto(WeekSummary week);

    List<WeekDto> toWeekDtos(List<WeekSummary> weeks);

    MonthRowDto toDto(MonthRow row);

    List<MonthRowDto> toRowDtos(List<MonthRow> rows);

    VacationSummaryDto toDto(VacationSummary vacations);

    default String month(YearMonth month) {
        return month == null ? null : month.toString();
    }
}
