package com.controlhorario.importexport.dto;

import java.time.LocalDate;

import com.controlhorario.absence.AbsenceType;

/**
 * Ausencia que se importaría: deducida del Excel de la empresa (vacaciones: día laborable del periodo sin
 * fichaje) o escrita en un Excel exportado por la app (cualquier tipo, también medio día).
 */
public record ImportAbsenceDto(LocalDate date, AbsenceType type, boolean halfDay, ImportAction action,
        String reason) {
}
