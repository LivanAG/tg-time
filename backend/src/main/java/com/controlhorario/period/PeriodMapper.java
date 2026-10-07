package com.controlhorario.period;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entidad → DTO de periodos y rangos de intensiva. */
@Mapper
public interface PeriodMapper {

    @Mapping(target = "intensiveRanges", source = "intensiveRanges")
    PeriodDto toDto(WorkPeriod period, List<IntensiveRange> intensiveRanges);

    IntensiveRangeDto toDto(IntensiveRange range);

    List<IntensiveRangeDto> toRangeDtos(List<IntensiveRange> ranges);
}
