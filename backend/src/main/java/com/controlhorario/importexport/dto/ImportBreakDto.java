package com.controlhorario.importexport.dto;

import java.time.LocalTime;

import com.controlhorario.workday.BreakType;
import com.fasterxml.jackson.annotation.JsonFormat;

public record ImportBreakDto(
        BreakType type,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
