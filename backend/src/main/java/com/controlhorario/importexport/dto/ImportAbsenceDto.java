package com.controlhorario.importexport.dto;

import java.time.LocalDate;

import com.controlhorario.absence.AbsenceType;

/** Ausencia deducida del Excel (vacaciones: día laborable del periodo sin fichaje). */
public record ImportAbsenceDto(LocalDate date, AbsenceType type, ImportAction action, String reason) {
}
