package com.controlhorario.workday.calc;

import java.time.LocalTime;

import com.controlhorario.workday.BreakType;

public record BreakInput(BreakType type, LocalTime start, LocalTime end) {
}
