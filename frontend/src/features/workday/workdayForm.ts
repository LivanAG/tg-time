import { z } from 'zod'

import type { BreakType, WorkLocation, WorkdayDto, WorkdayRequest } from '../../api/types'
import { apiFieldToPath } from '../../lib/formErrors'
import { durationInputValue, normalizeTime, parseDuration, TIME_PATTERN } from '../../lib/time'
import { validateWorkday, type CalcRules, type CalcWorkday } from '../../lib/workdayCalc'

export const NOTES_MAX = 500

export interface BreakFormValues {
  type: BreakType
  startTime: string
  endTime: string
}

/** Valores del editor: horas "HH:mm" y duraciones "h:mm" como texto, tal como se escriben. */
export interface WorkdayFormValues {
  startTime: string
  endTime: string
  breaks: BreakFormValues[]
  location: WorkLocation
  remoteMinutes: string
  notes: string
}

/** Rutas raíz del formulario (para colocar los errores 400 de la API junto a cada campo). */
export const WORKDAY_FIELDS = [
  'startTime',
  'endTime',
  'breaks',
  'location',
  'remoteMinutes',
  'notes',
] as const

export function toFormValues(workday: WorkdayDto | null): WorkdayFormValues {
  if (!workday) {
    return {
      startTime: '',
      endTime: '',
      breaks: [],
      location: 'OFICINA',
      remoteMinutes: '',
      notes: '',
    }
  }
  return {
    startTime: workday.startTime,
    endTime: workday.endTime,
    breaks: workday.breaks.map((b) => ({ type: b.type, startTime: b.startTime, endTime: b.endTime })),
    location: workday.location,
    remoteMinutes: durationInputValue(workday.remoteMinutes),
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
    remoteMinutes: values.location === 'MIXTO' ? parseDuration(values.remoteMinutes) : null,
  }
}

/** Cuerpo de PUT /api/workdays/{date}. remoteMinutes solo se envía con MIXTO. */
export function toRequest(values: WorkdayFormValues, version: number | null): WorkdayRequest {
  const notes = values.notes.trim()
  return {
    startTime: values.startTime ? normalizeTime(values.startTime) : null,
    endTime: values.endTime ? normalizeTime(values.endTime) : null,
    breaks: values.breaks.map((b) => ({
      type: b.type,
      startTime: normalizeTime(b.startTime),
      endTime: normalizeTime(b.endTime),
    })),
    location: values.location,
    remoteMinutes: values.location === 'MIXTO' ? parseDuration(values.remoteMinutes) : null,
    notes: notes === '' ? null : notes,
    version,
  }
}

function isTimeOrEmpty(value: string): boolean {
  return value === '' || TIME_PATTERN.test(value)
}

function isDurationOrEmpty(value: string): boolean {
  if (value.trim() === '') {
    return true
  }
  const minutes = parseDuration(value)
  return minutes !== null && minutes >= 0
}

const TIME_MESSAGE = 'Hora no válida (HH:mm)'
const DURATION_MESSAGE = 'Duración no válida (h:mm)'

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
      remoteMinutes: z.string(),
      notes: z.string().max(NOTES_MAX, `Máximo ${NOTES_MAX} caracteres`),
    })
    .superRefine((values, ctx) => {
      let formatErrors = false
      const add = (path: (string | number)[], message: string) => {
        ctx.addIssue({ code: 'custom', path, message })
      }
      if (!isTimeOrEmpty(values.startTime)) {
        add(['startTime'], TIME_MESSAGE)
        formatErrors = true
      }
      if (!isTimeOrEmpty(values.endTime)) {
        add(['endTime'], TIME_MESSAGE)
        formatErrors = true
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
      if (values.location === 'MIXTO' && !isDurationOrEmpty(values.remoteMinutes)) {
        add(['remoteMinutes'], DURATION_MESSAGE)
        formatErrors = true
      }
      if (formatErrors) {
        return
      }
      for (const issue of validateWorkday(toCalcInput(values as WorkdayFormValues), rules)) {
        add(issuePath(issue.field), issue.message)
      }
    })
}
