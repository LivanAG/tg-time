import type { BreakDto, DayDto, MonthSummaryDto, WeekSummaryDto } from '../../api/types'
import { absenceLabel, BREAK_SHORT } from '../../lib/format'

/** "D 12:43–13:02 · C 15:02–15:32" */
export function breaksSummary(breaks: BreakDto[]): string {
  return breaks.map((b) => `${BREAK_SHORT[b.type]} ${b.startTime}–${b.endTime}`).join(' · ')
}

/** Etiqueta de un día sin jornada normal: fin de semana, festivo, ausencia o fuera del periodo. */
export function dayLabel(day: DayDto): string | null {
  if (day.dayType === 'FUERA_DE_PERIODO') {
    return 'Fuera del periodo'
  }
  if (day.dayType === 'FESTIVO') {
    return day.holidayName ? `Festivo: ${day.holidayName}` : 'Festivo'
  }
  if (day.dayType === 'FIN_DE_SEMANA') {
    return 'Fin de semana'
  }
  if (day.absence) {
    return absenceLabel(day.absence.type, day.absence.halfDay)
  }
  return null
}

/** Días con fila editable: laborables sin ausencia de día completo, o cualquier día ya fichado del periodo. */
export function isInlineEditable(day: DayDto): boolean {
  if (day.dayType === 'FUERA_DE_PERIODO') {
    return false
  }
  if (day.workday) {
    return true
  }
  return day.dayType === 'LABORABLE' && (!day.absence || day.absence.halfDay)
}

/** Clases de fondo de la fila según el tipo de día. */
export function dayTone(day: DayDto): string {
  if (day.dayType === 'FUERA_DE_PERIODO') {
    return 'bg-slate-50 text-slate-400'
  }
  if (day.dayType === 'FESTIVO') {
    return 'bg-rose-50'
  }
  if (day.dayType === 'FIN_DE_SEMANA') {
    return 'bg-slate-50 text-slate-500'
  }
  if (day.absence) {
    switch (day.absence.type) {
      case 'VACACIONES':
        return 'bg-emerald-50'
      case 'PUENTE':
        return 'bg-violet-50'
      case 'PERMISO':
        return 'bg-cyan-50'
      case 'BAJA':
        return 'bg-orange-50'
    }
  }
  return 'bg-white'
}

export interface WeekGroup {
  week: WeekSummaryDto
  days: DayDto[]
}

/** Días del mes agrupados por las semanas del resumen (lunes a domingo, recortadas al mes). */
export function groupByWeek(summary: MonthSummaryDto): WeekGroup[] {
  return summary.weeks.map((week) => ({
    week,
    days: summary.days.filter((d) => d.date >= week.weekStart && d.date <= week.weekEnd),
  }))
}
