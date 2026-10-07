package com.controlhorario.calendar;

import java.util.List;

import org.mapstruct.Mapper;

/** Entidad → DTO de festivos. */
@Mapper
public interface HolidayMapper {

    HolidayDto toDto(Holiday holiday);

    List<HolidayDto> toDtos(List<Holiday> holidays);
}
