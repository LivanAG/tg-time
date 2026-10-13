// Puerto en TypeScript de WorkdayCalculator (backend/.../workday/calc/WorkdayCalculator.java):
// misma fórmula, mismas validaciones, mismos códigos y mismos mensajes. Sirve para el total en vivo
// del editor y para validar antes de enviar; el backend vuelve a calcular y es quien manda.
//
//   trabajado = bruto - max(0, desayuno - tolerancia) - max(comida, comidaMínima) - otrasPausas
//
// El bruto es la suma de los tramos trabajados: entrada-salida o, en MIXTO, el tramo de oficina más
// el de casa (el hueco entre ellos no cuenta). La comida mínima solo se aplica si hubo pausa COMIDA.
// Ubicación: OFICINA todo oficina, CASA todo casa, MIXTO lo trabajado en el tramo de casa (su duración
// menos lo que se descuenta de las pausas que caen en él) y el resto oficina.
//
// Horario computable (countFrom-countUntil, p. ej. 07:00-20:00): se puede fichar fuera de él, pero solo
// cuenta lo que cae dentro. Tramos y pausas se recortan a ese horario antes de calcular; una pausa que
// queda fuera no descuenta. La comida mínima se aplica a la comida fichada y solo se descuenta la parte
// que cae dentro del horario.

import type { BreakType, WorkLocation } from '../api/types'
import { parseTime } from './time'

export const END_BEFORE_START = 'END_BEFORE_START'
export const SEGMENTS_OVERLAP = 'SEGMENTS_OVERLAP'
export const BREAK_INVALID = 'BREAK_INVALID'
export const BREAK_OUTSIDE_WORKDAY = 'BREAK_OUTSIDE_WORKDAY'
export const BREAKS_OVERLAP = 'BREAKS_OVERLAP'
export const DUPLICATE_BREAKFAST = 'DUPLICATE_BREAKFAST'
export const DUPLICATE_LUNCH = 'DUPLICATE_LUNCH'
export const LUNCH_BELOW_MINIMUM = 'LUNCH_BELOW_MINIMUM'
export const OUTSIDE_COUNTED_HOURS = 'OUTSIDE_COUNTED_HOURS'

const DAY_MINUTES = 24 * 60

/** Error o aviso del cálculo (CalcIssue del backend). */
export interface CalcIssue {
  code: string
  field: string | null
  message: string
}

export interface CalcRules {
  breakfastToleranceMin: number
  minLunchMin: number
  /** Horario computable ("HH:mm"): lo fichado fuera no cuenta. Sin él, cuenta todo. */
  countFrom?: string | null
  countUntil?: string | null
}

/** Horario computable en minutos del día. */
interface CountedWindow {
  from: number
  until: number
}

function countedWindow(rules: CalcRules): CountedWindow {
  return { from: parseTime(rules.countFrom) ?? 0, until: parseTime(rules.countUntil) ?? DAY_MINUTES }
}

/** Minutos de [start, end) que caen dentro del horario computable. */
function counted(start: number, end: number, window: CountedWindow): number {
  return Math.max(0, Math.min(end, window.until) - Math.max(start, window.from))
}

/** 420 → "7:00" (igual que Minutes.format del backend). */
function hm(minutes: number): string {
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
}

export interface CalcBreak {
  type: BreakType | null
  /** "HH:mm" */
  startTime: string | null
  /** "HH:mm" */
  endTime: string | null
}

/** Tramos de un día MIXTO ("HH:mm"): oficina y casa, en cualquier orden. */
export interface CalcMixed {
  officeStart: string | null
  officeEnd: string | null
  homeStart: string | null
  homeEnd: string | null
}

/** Fichaje de un día tal como lo introduce el usuario (horas "HH:mm"). */
export interface CalcWorkday {
  /** En MIXTO no se usan: la entrada y la salida salen de los tramos. */
  startTime: string | null
  endTime: string | null
  breaks: CalcBreak[]
  location: WorkLocation | null
  /** Solo con MIXTO. */
  mixed: CalcMixed | null
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

interface Segment {
  location: WorkLocation
  start: number
  end: number
}

interface ParsedMixed {
  officeStart: number | null
  officeEnd: number | null
  homeStart: number | null
  homeEnd: number | null
}

interface ParsedWorkday {
  start: number | null
  end: number | null
  breaks: ParsedBreak[]
  location: WorkLocation
  mixed: ParsedMixed
}

function parse(input: CalcWorkday): ParsedWorkday {
  return {
    start: parseTime(input.startTime),
    end: parseTime(input.endTime),
    breaks: input.breaks.map((b) => ({ type: b.type, start: parseTime(b.startTime), end: parseTime(b.endTime) })),
    location: input.location ?? 'OFICINA',
    mixed: {
      officeStart: parseTime(input.mixed?.officeStart ?? null),
      officeEnd: parseTime(input.mixed?.officeEnd ?? null),
      homeStart: parseTime(input.mixed?.homeStart ?? null),
      homeEnd: parseTime(input.mixed?.homeEnd ?? null),
    },
  }
}

/** Primera entrada y última salida de un día MIXTO ("HH:mm"); null si falta alguna hora. */
export function mixedBounds(mixed: CalcMixed | null): { startTime: string; endTime: string } | null {
  if (!mixed?.officeStart || !mixed.officeEnd || !mixed.homeStart || !mixed.homeEnd) {
    return null
  }
  // "HH:mm" se ordena igual como texto que como hora.
  return {
    startTime: mixed.officeStart < mixed.homeStart ? mixed.officeStart : mixed.homeStart,
    endTime: mixed.officeEnd > mixed.homeEnd ? mixed.officeEnd : mixed.homeEnd,
  }
}

function validateStartEnd(day: ParsedWorkday): CalcIssue[] {
  if (day.start === null || day.end === null) {
    return [{ code: END_BEFORE_START, field: 'startTime', message: 'La entrada y la salida son obligatorias' }]
  }
  if (!(day.end > day.start)) {
    return [{ code: END_BEFORE_START, field: 'endTime', message: 'La salida debe ser posterior a la entrada' }]
  }
  return []
}

function validateMixed(m: ParsedMixed): CalcIssue[] {
  const errors: CalcIssue[] = []
  if (m.officeStart === null || m.officeEnd === null) {
    errors.push({ code: END_BEFORE_START, field: 'officeStart', message: 'Indica la entrada y la salida en la oficina' })
  } else if (!(m.officeEnd > m.officeStart)) {
    errors.push({
      code: END_BEFORE_START,
      field: 'officeEnd',
      message: 'La salida de la oficina debe ser posterior a la entrada',
    })
  }
  if (m.homeStart === null || m.homeEnd === null) {
    errors.push({ code: END_BEFORE_START, field: 'homeStart', message: 'Indica la entrada y la salida en casa' })
  } else if (!(m.homeEnd > m.homeStart)) {
    errors.push({ code: END_BEFORE_START, field: 'homeEnd', message: 'La salida de casa debe ser posterior a la entrada' })
  }
  if (
    errors.length === 0 &&
    (m.officeStart as number) < (m.homeEnd as number) &&
    (m.homeStart as number) < (m.officeEnd as number)
  ) {
    errors.push({
      code: SEGMENTS_OVERLAP,
      field: 'homeStart',
      message: 'Los tramos de oficina y de casa no pueden solaparse',
    })
  }
  return errors
}

/** Tramos trabajados: entrada-salida o, en MIXTO, oficina y casa. Supone horas válidas. */
function segments(day: ParsedWorkday): Segment[] {
  if (day.location === 'MIXTO') {
    const m = day.mixed
    return [
      { location: 'OFICINA', start: m.officeStart as number, end: m.officeEnd as number },
      { location: 'CASA', start: m.homeStart as number, end: m.homeEnd as number },
    ]
  }
  return [{ location: day.location, start: day.start as number, end: day.end as number }]
}

function contains(segment: Segment, b: ParsedBreak): boolean {
  return (b.start as number) >= segment.start && (b.end as number) <= segment.end
}

/** Errores que impiden guardar el día. Lista vacía si es válido. */
export function validateWorkday(input: CalcWorkday, _rules: CalcRules): CalcIssue[] {
  const day = parse(input)
  const errors = day.location === 'MIXTO' ? validateMixed(day.mixed) : validateStartEnd(day)
  if (errors.length > 0) {
    return errors
  }
  const daySegments = segments(day)
  let breakfasts = 0
  let lunches = 0
  day.breaks.forEach((b, i) => {
    const field = `breaks[${i}]`
    if (b.type === null || b.start === null || b.end === null || !(b.end > b.start)) {
      errors.push({ code: BREAK_INVALID, field, message: 'La pausa necesita tipo y un fin posterior al inicio' })
      return
    }
    if (!daySegments.some((s) => contains(s, b))) {
      errors.push({
        code: BREAK_OUTSIDE_WORKDAY,
        field,
        message:
          day.location === 'MIXTO'
            ? 'La pausa debe estar dentro del tramo de oficina o del de casa'
            : 'La pausa debe estar dentro de la jornada',
      })
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
  return errors
}

/** Totales del día. Supone una entrada válida (ver validateWorkday). */
export function calculateWorkday(input: CalcWorkday, rules: CalcRules): WorkdayResult {
  const day = parse(input)
  const daySegments = segments(day)
  const window = countedWindow(rules)
  const gross = daySegments.reduce((sum, s) => sum + counted(s.start, s.end, window), 0)
  let breakfast = 0
  let lunch = 0
  let lunchRecorded = 0
  let hasLunch = false
  let other = 0
  for (const b of day.breaks) {
    const duration = counted(b.start as number, b.end as number, window)
    switch (b.type) {
      case 'DESAYUNO':
        breakfast += duration
        break
      case 'COMIDA':
        lunch += duration
        lunchRecorded += (b.end as number) - (b.start as number)
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
  // La comida mínima vale para la comida fichada; se descuenta lo que cae dentro del horario.
  const lunchDeducted =
    hasLunch && lunch > 0 ? Math.max(lunchRecorded, rules.minLunchMin) - (lunchRecorded - lunch) : 0
  const worked = Math.max(0, gross - breakfastDeducted - lunchDeducted - other)

  const warnings: CalcIssue[] = []
  if (hasLunch && lunch > 0 && lunchRecorded < rules.minLunchMin) {
    warnings.push({
      code: LUNCH_BELOW_MINIMUM,
      field: 'breaks',
      message: `La comida dura ${lunchRecorded} min: se descuenta el mínimo de ${rules.minLunchMin} min`,
    })
  }
  warnings.push(...outsideWarnings(day, daySegments, window))

  let remote: number
  switch (day.location) {
    case 'CASA':
      remote = worked
      break
    case 'MIXTO': {
      // Lo trabajado en casa: el tramo menos lo que se descuenta de las pausas que caen en él
      // (solo hay un desayuno y una comida, así que su descuento va entero a su tramo).
      const home = daySegments[1]
      let homeWorked = counted(home.start, home.end, window)
      for (const b of day.breaks) {
        if (contains(home, b)) {
          if (b.type === 'DESAYUNO') {
            homeWorked -= breakfastDeducted
          } else if (b.type === 'COMIDA') {
            homeWorked -= lunchDeducted
          } else {
            homeWorked -= counted(b.start as number, b.end as number, window)
          }
        }
      }
      remote = Math.min(worked, Math.max(0, homeWorked))
      break
    }
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

/** Avisos (no impiden guardar) si se fichó antes o después del horario computable. */
function outsideWarnings(day: ParsedWorkday, daySegments: Segment[], window: CountedWindow): CalcIssue[] {
  const mixed = day.location === 'MIXTO'
  const warnings: CalcIssue[] = []
  const first = daySegments.reduce((a, b) => (b.start < a.start ? b : a))
  const last = daySegments.reduce((a, b) => (b.end > a.end ? b : a))
  if (first.start < window.from) {
    warnings.push({
      code: OUTSIDE_COUNTED_HOURS,
      field: !mixed ? 'startTime' : first.location === 'CASA' ? 'homeStart' : 'officeStart',
      message: `Antes de las ${hm(window.from)} no cuenta: se computa desde las ${hm(window.from)}`,
    })
  }
  if (last.end > window.until) {
    warnings.push({
      code: OUTSIDE_COUNTED_HOURS,
      field: !mixed ? 'endTime' : last.location === 'CASA' ? 'homeEnd' : 'officeEnd',
      message: `Después de las ${hm(window.until)} no cuenta: se computa hasta las ${hm(window.until)}`,
    })
  }
  return warnings
}

export interface LiveCalculation {
  errors: CalcIssue[]
  /** null si los datos aún no permiten calcular (faltan horas, tramos o hay pausas no válidas). */
  result: WorkdayResult | null
}

/** Validación + cálculo para el editor: calcula en cuanto el día es válido. */
export function liveCalculation(input: CalcWorkday, rules: CalcRules): LiveCalculation {
  const errors = validateWorkday(input, rules)
  return { errors, result: errors.length === 0 ? calculateWorkday(input, rules) : null }
}
