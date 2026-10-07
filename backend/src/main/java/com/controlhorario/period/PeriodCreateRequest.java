package com.controlhorario.period;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de un periodo: {@code PeriodDto} sin id ni versión, con los rangos de intensiva iniciales y
 * {@code preloadHolidays} (por defecto true: precarga los festivos de Madrid del periodo).
 */
public record PeriodCreateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite como máximo 100 caracteres")
        String name,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate endDate,

        @NotNull(message = "Las horas de convenio son obligatorias")
        @Min(value = 1, message = "Las horas de convenio deben ser mayores que cero")
        @Max(value = PeriodService.MAX_AGREEMENT_MINUTES, message = "Las horas de convenio no pueden superar un año completo")
        Integer agreementMinutes,

        @NotNull(message = "Los días de vacaciones son obligatorios")
        @Min(value = 0, message = "Los días de vacaciones deben estar entre 0 y 366")
        @Max(value = 366, message = "Los días de vacaciones deben estar entre 0 y 366")
        Integer vacationDays,

        @NotNull(message = "La jornada normal es obligatoria")
        @Min(value = 1, message = "La jornada normal debe estar entre 1 y 1440 minutos")
        @Max(value = 1440, message = "La jornada normal debe estar entre 1 y 1440 minutos")
        Integer normalDayMinutes,

        @NotNull(message = "La jornada intensiva es obligatoria")
        @Min(value = 1, message = "La jornada intensiva debe estar entre 1 y 1440 minutos")
        @Max(value = 1440, message = "La jornada intensiva debe estar entre 1 y 1440 minutos")
        Integer intensiveDayMinutes,

        @NotNull(message = "La tolerancia de desayuno es obligatoria")
        @Min(value = 0, message = "La tolerancia de desayuno debe estar entre 0 y 240 minutos")
        @Max(value = 240, message = "La tolerancia de desayuno debe estar entre 0 y 240 minutos")
        Integer breakfastToleranceMin,

        @NotNull(message = "La comida mínima es obligatoria")
        @Min(value = 0, message = "La comida mínima debe estar entre 0 y 240 minutos")
        @Max(value = 240, message = "La comida mínima debe estar entre 0 y 240 minutos")
        Integer minLunchMin,

        @NotNull(message = "El tramo de redondeo es obligatorio")
        @Min(value = 1, message = "El tramo de redondeo debe estar entre 1 y 60 minutos")
        @Max(value = 60, message = "El tramo de redondeo debe estar entre 1 y 60 minutos")
        Integer roundingStepMin,

        @NotNull(message = "El porcentaje máximo de teletrabajo es obligatorio")
        @Min(value = 0, message = "El porcentaje máximo de teletrabajo debe estar entre 0 y 100")
        @Max(value = 100, message = "El porcentaje máximo de teletrabajo debe estar entre 0 y 100")
        Integer maxRemotePct,

        @NotNull(message = "Los días máximos de teletrabajo al mes son obligatorios")
        @Min(value = 0, message = "Los días máximos de teletrabajo al mes deben estar entre 0 y 31")
        @Max(value = 31, message = "Los días máximos de teletrabajo al mes deben estar entre 0 y 31")
        Integer maxRemoteDaysMonth,

        @Min(value = -PeriodService.MAX_AGREEMENT_MINUTES, message = "El saldo inicial no es válido")
        @Max(value = PeriodService.MAX_AGREEMENT_MINUTES, message = "El saldo inicial no es válido")
        Integer openingBalanceMin,

        List<@Valid IntensiveRangeDto> intensiveRanges,

        Boolean preloadHolidays) implements PeriodParameters {
}
