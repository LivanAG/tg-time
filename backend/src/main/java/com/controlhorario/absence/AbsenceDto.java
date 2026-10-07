package com.controlhorario.absence;

import java.time.LocalDate;

/** Ausencia de un día (vacaciones, puente recuperable, permiso o baja), de día completo o medio día. */
public record AbsenceDto(LocalDate date, AbsenceType type, boolean halfDay, String note) {
}
