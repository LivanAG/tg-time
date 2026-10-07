// Puerto en TypeScript de WorkdayCalculator (backend/.../workday/calc/WorkdayCalculator.java):
// misma fórmula, mismas validaciones, mismos códigos y mismos mensajes. Sirve para el total en vivo
// del editor y para validar antes de enviar; el backend vuelve a calcular y es quien manda.
//
//   trabajado = (salida - entrada) - max(0, desayuno - tolerancia) - max(comida, comidaMínima) - otrasPausas
//
// La comida mínima solo se aplica si hubo pausa COMIDA. Ubicación: OFICINA todo oficina, CASA todo
// casa, MIXTO los minutos en casa indicados y el resto oficina.

import type { BreakType, WorkLocation } from '../api/types'
import { parseTime } from './time'

export const END_BEFORE_START = 'END_BEFORE_START'
export const BREAK_INVALID = 'BREAK_INVALID'
export const BREAK_OUTSIDE_WORKDAY = 'BREAK_OUTSIDE_WORKDAY'
export const BREAKS_OVERLAP = 'BREAKS_OVERLAP'
export const DUPLICATE_BREAKFAST = 'DUPLICATE_BREAKFAST'
export const DUPLICATE_LUNCH = 'DUPLICATE_LUNCH'
export const REMOTE_MINUTES_REQUIRED = 'REMOTE_MINUTES_REQUIRED'
export const REMOTE_MINUTES_EXCEED_WORKED = 'REMOTE_MINUTES_EXCEED_WORKED'
export const LUNCH_BELOW_MINIMUM = 'LUNCH_BELOW_MINIMUM'

/** Error o aviso del cálculo (CalcIssue del backend). */
export interface CalcIssue {
  code: string
  field: string | null
  message: string
}

export interface CalcRules {
  breakfastToleranceMin: number
  minLunchMin: number
}

export interface CalcBreak {
  type: BreakType | null
  /** "HH:mm" */
  startTime: string | null
  /** "HH:mm" */
  endTime: string | null
}

/** Fichaje de un día tal como lo introduce el usuario (horas "HH:mm"). */
export interface CalcWorkday {
  startTime: string | null
  endTime: string | null
  breaks: CalcBreak[]
  location: WorkLocation | null
  remoteMinutes: number | null
}

export interface WorkdayResult {
  grossMinutes: number
  breakfastMinutes: number
  breakfastDeductedMinutes: number
  lunchMinutes: number
  lunchDeductedMinutes: number
  otherBreakMinutes: number
  workedMinutes: number
  officeMinutes: number
  remoteMinutes: number
  warnings: CalcIssue[]
}

interface ParsedBreak {
  type: BreakType | null
  start: number | null
  end: number | null
}

interface ParsedWorkday {
  start: number | null
  end: number | null
  breaks: ParsedBreak[]
  location: WorkLocation
  remoteMinutes: number | null
}

function parse(input: CalcWorkday): ParsedWorkday {
  return {
    start: parseTime(input.startTime),
    end: parseTime(input.endTime),
    breaks: input.breaks.map((b) => ({ type: b.type, start: parseTime(b.startTime), end: parseTime(b.endTime) })),
    location: input.location ?? 'OFICINA',
    remoteMinutes: input.remoteMinutes,
  }
}

/** Errores que impiden guardar el día. Lista vacía si es válido. */
export function validateWorkday(input: CalcWorkday, rules: CalcRules): CalcIssue[] {
  const day = parse(input)
  const errors: CalcIssue[] = []
  if (day.start === null || day.end === null) {
    errors.push({ code: END_BEFORE_START, field: 'startTime', message: 'La entrada y la salida son obligatorias' })
    return errors
  }
  if (!(day.end > day.start)) {
    errors.push({ code: END_BEFORE_START, field: 'endTime', message: 'La salida debe ser posterior a la entrada' })
    return errors
  }
  let breakfasts = 0
  let lunches = 0
  day.breaks.forEach((b, i) => {
    const field = `breaks[${i}]`
    if (b.type === null || b.start === null || b.end === null || !(b.end > b.start)) {
      errors.push({ code: BREAK_INVALID, field, message: 'La pausa necesita tipo y un fin posterior al inicio' })
      return
    }
    if (b.start < (day.start as number) || b.end > (day.end as number)) {
      errors.push({ code: BREAK_OUTSIDE_WORKDAY, field, message: 'La pausa debe estar dentro de la jornada' })
    }
    if (b.type === 'DESAYUNO' && ++breakfasts > 1) {
      errors.push({ code: DUPLICATE_BREAKFAST, field, message: 'Solo puede haber un desayuno' })
    }
    if (b.type === 'COMIDA' && ++lunches > 1) {
      errors.push({ code: DUPLICATE_LUNCH, field, message: 'Solo puede haber una comida' })
    }
  })
  const ordered: number[] = []
  day.breaks.forEach((b, i) => {
    if (b.start !== null && b.end !== null && b.end > b.start) {
      ordered.push(i)
    }
  })
  // Array.prototype.sort es estable, igual que List.sort en Java.
  ordered.sort((a, b) => (day.breaks[a].start as number) - (day.breaks[b].start as number))
  for (let k = 1; k < ordered.length; k++) {
    const prev = day.breaks[ordered[k - 1]]
    const cur = day.breaks[ordered[k]]
    if ((cur.start as number) < (prev.end as number)) {
      errors.push({ code: BREAKS_OVERLAP, field: `breaks[${ordered[k]}]`, message: 'Las pausas no pueden solaparse' })
    }
  }
  if (day.location === 'MIXTO') {
    if (day.remoteMinutes === null) {
      errors.push({
        code: REMOTE_MINUTES_REQUIRED,
        field: 'remoteMinutes',
        message: 'Indica cuántos minutos has trabajado en casa',
      })
    } else if (errors.length === 0 && day.remoteMinutes > calculateParsed(day, rules).workedMinutes) {
      errors.push({
        code: REMOTE_MINUTES_EXCEED_WORKED,
        field: 'remoteMinutes',
        message: 'Los minutos en casa no pueden superar el tiempo trabajado',
      })
    }
  }
  return errors
}

/** Totales del día. Supone una entrada válida (ver validateWorkday). */
export function calculateWorkday(input: CalcWorkday, rules: CalcRules): WorkdayResult {
  return calculateParsed(parse(input), rules)
}

function calculateParsed(day: ParsedWorkday, rules: CalcRules): WorkdayResult {
  const gross = (day.end as number) - (day.start as number)
  let breakfast = 0
  let lunch = 0
  let hasLunch = false
  let other = 0
  for (const b of day.breaks) {
    const duration = (b.end as number) - (b.start as number)
    switch (b.type) {
      case 'DESAYUNO':
        breakfast += duration
        break
      case 'COMIDA':
        lunch += duration
        hasLunch = true
        break
      case 'OTRA':
        other += duration
        break
      default:
        break
    }
  }
  const breakfastDeducted = Math.max(0, breakfast - rules.breakfastToleranceMin)
  const lunchDeducted = hasLunch ? Math.max(lunch, rules.minLunchMin) : 0
  const worked = Math.max(0, gross - breakfastDeducted - lunchDeducted - other)

  const warnings: CalcIssue[] = []
  if (hasLunch && lunch < rules.minLunchMin) {
    warnings.push({
      code: LUNCH_BELOW_MINIMUM,
      field: 'breaks',
      message: `La comida dura ${lunch} min: se descuenta el mínimo de ${rules.minLunchMin} min`,
    })
  }

  let remote: number
  switch (day.location) {
    case 'CASA':
      remote = worked
      break
    case 'MIXTO':
      remote = Math.min(worked, Math.max(0, day.remoteMinutes ?? 0))
      break
    default:
      remote = 0
  }
  return {
    grossMinutes: gross,
    breakfastMinutes: breakfast,
    breakfastDeductedMinutes: breakfastDeducted,
    lunchMinutes: lunch,
    lunchDeductedMinutes: lunchDeducted,
    otherBreakMinutes: other,
    workedMinutes: worked,
    officeMinutes: worked - remote,
    remoteMinutes: remote,
    warnings,
  }
}

export interface LiveCalculation {
  errors: CalcIssue[]
  /** null si los datos aún no permiten calcular (faltan horas o hay pausas no válidas). */
  result: WorkdayResult | null
}

/**
 * Validación + cálculo para el editor: calcula en cuanto las horas y las pausas son válidas, aunque
 * aún falten los minutos en casa del modo MIXTO.
 */
export function liveCalculation(input: CalcWorkday, rules: CalcRules): LiveCalculation {
  const errors = validateWorkday(input, rules)
  const calculable = errors.every((e) => e.field === 'remoteMinutes')
  return { errors, result: calculable ? calculateWorkday(input, rules) : null }
}
