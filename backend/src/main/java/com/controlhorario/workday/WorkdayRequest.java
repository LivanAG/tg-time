package com.controlhorario.workday;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Cuerpo de {@code PUT /api/workdays/{date}}.
 *
 * @param startTime   entrada (obligatoria salvo en MIXTO, donde es la primera entrada de los tramos)
 * @param endTime     salida (obligatoria salvo en MIXTO, donde es la última salida de los tramos)
 * @param location    OFICINA si llega null
 * @param officeStart solo con MIXTO: entrada en la oficina (igual officeEnd, homeStart y homeEnd)
 * @param version     null al crear; la del último GET al modificar
 */
public record WorkdayRequest(
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,

        @JsonFormat(pattern = "HH:mm") LocalTime endTime,

        @Size(max = 20, message = "Como máximo 20 pausas por día")
        List<@NotNull(message = "La pausa no puede estar vacía") BreakDto> breaks,

        Location location,

        @JsonFormat(pattern = "HH:mm") LocalTime officeStart,

        @JsonFormat(pattern = "HH:mm") LocalTime officeEnd,

        @JsonFormat(pattern = "HH:mm") LocalTime homeStart,

        @JsonFormat(pattern = "HH:mm") LocalTime homeEnd,

        @Size(max = 500, message = "Las notas admiten como máximo 500 caracteres")
        String notes,

        Long version) {
}
