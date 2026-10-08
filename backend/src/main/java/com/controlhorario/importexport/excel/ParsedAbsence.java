package com.controlhorario.importexport.excel;

import java.time.LocalDate;

import com.controlhorario.absence.AbsenceType;

/** Ausencia escrita en un Excel exportado por la app (columna Ausencia). */
public record ParsedAbsence(LocalDate date, String sheet, int row, AbsenceType type, boolean halfDay) {
}
