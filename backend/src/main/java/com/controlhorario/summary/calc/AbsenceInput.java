package com.controlhorario.summary.calc;

import java.time.LocalDate;

import com.controlhorario.absence.AbsenceType;

public record AbsenceInput(LocalDate date, AbsenceType type, boolean halfDay) {
}
