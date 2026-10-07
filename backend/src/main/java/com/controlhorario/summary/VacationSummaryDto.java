package com.controlhorario.summary;

/** Vacaciones del periodo (medio día = 0,5). */
public record VacationSummaryDto(
        int totalDays,
        double plannedDays,
        int plannedMinutes,
        double takenDays,
        int takenMinutes,
        double pendingPlannedDays,
        int pendingPlannedMinutes,
        double remainingDays,
        int remainingMinutes,
        double unplannedDays,
        int unplannedMinutes,
        int valueMinutes) {
}
