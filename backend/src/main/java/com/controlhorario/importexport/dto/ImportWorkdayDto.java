package com.controlhorario.importexport.dto;

import java.time.LocalTime;
import java.util.List;

import com.controlhorario.workday.Location;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Fichaje leído del Excel tal como se guardaría (mismos campos que el cuerpo de PUT /api/workdays/{date}). */
public record ImportWorkdayDto(
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        List<ImportBreakDto> breaks,
        Location location,
        @JsonFormat(pattern = "HH:mm") LocalTime officeStart,
        @JsonFormat(pattern = "HH:mm") LocalTime officeEnd,
        @JsonFormat(pattern = "HH:mm") LocalTime homeStart,
        @JsonFormat(pattern = "HH:mm") LocalTime homeEnd,
        String notes) {
}
