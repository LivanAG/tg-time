package com.controlhorario.summary.calc;

/**
 * Vacaciones del periodo. Días con medio día = 0,5.
 *
 * @param plannedDays    días marcados como VACACIONES (pasados y futuros)
 * @param takenDays      disfrutadas: VACACIONES hasta hoy incluido
 * @param pendingPlannedDays planificadas a partir de mañana
 * @param remainingDays  restantes = total - disfrutadas
 * @param remainingMinutes valor de las restantes: planificadas futuras + sin planificar a jornada normal
 * @param unplannedDays  total - marcadas (no negativo)
 * @param valueMinutes   valor de las vacaciones: marcadas + sin planificar estimadas a jornada normal
 */
public record VacationSummary(
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
