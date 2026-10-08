import { z } from 'zod'

import type { BreakType, WorkLocation, WorkdayDto, WorkdayRequest } from '../../api/types'
import { apiFieldToPath } from '../../lib/formErrors'
import { normalizeTime, TIME_PATTERN } from '../../lib/time'
import { mixedBounds, validateWorkday, type CalcRules, type CalcWorkday } from '../../lib/workdayCalc'

export const NOTES_MAX = 500

export interface BreakFormValues {
  type: BreakType
  startTime: string
  endTime: string
}

/**
 * Valores del editor: horas "HH:mm" como texto, tal como se escriben. En MIXTO se usan los tramos
 * (office*, home*) y no startTime/endTime.
 */
export interface WorkdayFormValues {
  startTime: string
  endTime: string
  breaks: BreakFormValues[]
  location: WorkLocation
  officeStart: string
  officeEnd: string
  homeStart: string
  homeEnd: string
  notes: string
}

/** Rutas raíz del formulario (para colocar los errores 400 de la API junto a cada campo). */
export const WORKDAY_FIELDS = [
  'startTime',
  'endTime',
  'breaks',
  'location',
  'officeStart',
  'officeEnd',
  'homeStart',
  'homeEnd',
  'notes',
] as const

export function toFormValues(workday: WorkdayDto | null): WorkdayFormValues {
  if (!workday) {
    return {
      startTime: '',
      endTime: '',
      breaks: [],
      location: 'OFICINA',
      officeStart: '',
      officeEnd: '',
      homeStart: '',
      homeEnd: '',
      notes: '',
    }
  }
  return {
    startTime: workday.startTime,
    endTime: workday.endTime,
    breaks: workday.breaks.map((b) => ({ type: b.type, startTime: b.startTime, endTime: b.endTime })),
    location: workday.location,
    officeStart: workday.officeStart ?? '',
    officeEnd: workday.officeEnd ?? '',
    homeStart: workday.homeStart ?? '',
    homeEnd: workday.homeEnd ?? '',
    notes: workday.notes ?? '',
  }
}

/** Entrada del motor de cálculo a partir de los valores del formulario. */
export function toCalcInput(values: WorkdayFormValues): CalcWorkday {
  return {
    startTime: values.startTime || null,
    endTime: values.endTime || null,
    breaks: values.breaks.map((b) => ({ type: b.type, startTime: b.startTime || null, endTime: b.endTime || null })),
    location: values.location,
    mixed:
      values.location === 'MIXTO'
        ? {
            officeStart: values.officeStart || null,
            officeEnd: values.officeEnd || null,
            homeStart: values.homeStart || null,
            homeEnd: values.homeEnd || null,
          }
        : null,
  }
}

const timeOrNull = (value: string) => (value ? normalizeTime(value) : null)

/** Cuerpo de PUT /api/workdays/{date}. En MIXTO se envían los tramos y la entrada/salida que salen de ellos. */
export function toRequest(values: WorkdayFormValues, version: number | null): WorkdayRequest {
  const notes = values.notes.trim()
  const mixed = values.location === 'MIXTO'
  const bounds = mixed ? mixedBounds(toCalcInput(values).mixed) : null
  return {
    startTime: mixed ? (bounds ? normalizeTime(bounds.startTime) : null) : timeOrNull(values.startTime),
    endTime: mixed ? (bounds ? normalizeTime(bounds.endTime) : null) : timeOrNull(values.endTime),
    breaks: values.breaks.map((b) => ({
      type: b.type,
      startTime: normalizeTime(b.startTime),
      endTime: normalizeTime(b.endTime),
    })),
    location: values.location,
    officeStart: mixed ? timeOrNull(values.officeStart) : null,
    officeEnd: mixed ? timeOrNull(values.officeEnd) : null,
    homeStart: mixed ? timeOrNull(values.homeStart) : null,
    homeEnd: mixed ? timeOrNull(values.homeEnd) : null,
    notes: notes === '' ? null : notes,
    version,
  }
}

function isTimeOrEmpty(value: string): boolean {
  return value === '' || TIME_PATTERN.test(value)
}

const TIME_MESSAGE = 'Hora no válida (HH:mm)'
const MIXED_FIELDS = ['officeStart', 'officeEnd', 'homeStart', 'homeEnd'] as const

/** Path de Zod a partir del campo de un error del cálculo ("breaks[1]" → ["breaks", 1]). */
function issuePath(field: string | null): (string | number)[] {
  if (!field) {
    return []
  }
  return apiFieldToPath(field)
    .split('.')
    .map((part) => (/^\d+$/.test(part) ? Number(part) : part))
}

/**
 * Validación del editor: formato de cada campo y, si el formato es correcto, las mismas reglas y
 * mensajes que WorkdayCalculator.validate en el backend (puerto en lib/workdayCalc).
 */
export function workdaySchema(rules: CalcRules) {
  return z
    .object({
      startTime: z.string(),
      endTime: z.string(),
      breaks: z.array(
        z.object({
          type: z.enum(['DESAYUNO', 'COMIDA', 'OTRA']),
          startTime: z.string(),
          endTime: z.string(),
        }),
      ),
      location: z.enum(['OFICINA', 'CASA', 'MIXTO']),
      officeStart: z.string(),
      officeEnd: z.string(),
      homeStart: z.string(),
      homeEnd: z.string(),
      notes: z.string().max(NOTES_MAX, `Máximo ${NOTES_MAX} caracteres`),
    })
    .superRefine((values, ctx) => {
      let formatErrors = false
      const add = (path: (string | number)[], message: string) => {
        ctx.addIssue({ code: 'custom', path, message })
      }
      const timeFields = values.location === 'MIXTO' ? MIXED_FIELDS : (['startTime', 'endTime'] as const)
      for (const field of timeFields) {
        if (!isTimeOrEmpty(values[field])) {
          add([field], TIME_MESSAGE)
          formatErrors = true
        }
      }
      values.breaks.forEach((b, i) => {
        if (!isTimeOrEmpty(b.startTime)) {
          add(['breaks', i, 'startTime'], TIME_MESSAGE)
          formatErrors = true
        }
        if (!isTimeOrEmpty(b.endTime)) {
          add(['breaks', i, 'endTime'], TIME_MESSAGE)
          formatErrors = true
        }
      })
      if (formatErrors) {
        return
      }
      for (const issue of validateWorkday(toCalcInput(values as WorkdayFormValues), rules)) {
        add(issuePath(issue.field), issue.message)
      }
    })
}
