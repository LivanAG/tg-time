package com.controlhorario.workday;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Cuerpo de {@code PUT /api/workdays/{date}}.
 *
 * @param location      OFICINA si llega null
 * @param remoteMinutes minutos en casa; solo se guarda con MIXTO
 * @param version       null al crear; la del último GET al modificar
 */
public record WorkdayRequest(
        @NotNull(message = "La entrada es obligatoria")
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,

        @NotNull(message = "La salida es obligatoria")
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,

        @Size(max = 20, message = "Como máximo 20 pausas por día")
        List<@NotNull(message = "La pausa no puede estar vacía") BreakDto> breaks,

        Location location,

        @PositiveOrZero(message = "Los minutos no pueden ser negativos")
        @Max(value = 1440, message = "No puede superar 24 horas")
        Integer remoteMinutes,

        @PositiveOrZero(message = "Los minutos no pueden ser negativos")
        @Max(value = 1440, message = "No puede superar 24 horas")
        Integer jiraMinutes,

        @PositiveOrZero(message = "Los minutos no pueden ser negativos")
        @Max(value = 1440, message = "No puede superar 24 horas")
        Integer izertiaMinutes,

        @Size(max = 500, message = "Las notas admiten como máximo 500 caracteres")
        String notes,

        Long version) {
}
