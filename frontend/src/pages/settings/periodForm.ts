import { z } from 'zod'

import type { CreatePeriodRequest, IntensiveRangeDto, PeriodDto, UpdatePeriodRequest } from '../../api/types'
import { addDaysIso, oneYearPeriodEnd, shiftYears } from '../../lib/dates'
import { formatMinutes, parseDuration } from '../../lib/time'

/**
 * Formulario de periodo. Los nombres coinciden con los de la API para colocar sus errores junto a
 * cada campo. Las jornadas, el convenio y el saldo inicial se editan como h:mm; tolerancias, redondeo,
 * porcentajes y días, como enteros.
 */
export interface PeriodFormValues {
  name: string
  startDate: string
  endDate: string
  agreementMinutes: string
  vacationDays: string
  normalDayMinutes: string
  intensiveDayMinutes: string
  breakfastToleranceMin: string
  minLunchMin: string
  roundingStepMin: string
  maxRemotePct: string
  maxRemoteDaysMonth: string
  openingBalanceMin: string
  intensiveRanges: IntensiveRangeDto[]
  preloadHolidays: boolean
}

export const PERIOD_FIELDS = [
  'name',
  'startDate',
  'endDate',
  'agreementMinutes',
  'vacationDays',
  'normalDayMinutes',
  'intensiveDayMinutes',
  'breakfastToleranceMin',
  'minLunchMin',
  'roundingStepMin',
  'maxRemotePct',
  'maxRemoteDaysMonth',
  'openingBalanceMin',
  'intensiveRanges',
  'preloadHolidays',
] as const

/** Valores del Excel HORAS_IZERTIS_2026-27 para el primer periodo. */
export const EXCEL_DEFAULTS: PeriodFormValues = {
  name: '2026-2027',
  startDate: '2026-05-26',
  endDate: '2027-05-25',
  agreementMinutes: '1760:00',
  vacationDays: '23',
  normalDayMinutes: '8:00',
  intensiveDayMinutes: '7:00',
  breakfastToleranceMin: '20',
  minLunchMin: '30',
  roundingStepMin: '15',
  maxRemotePct: '50',
  maxRemoteDaysMonth: '8',
  openingBalanceMin: '0:00',
  intensiveRanges: [{ startDate: '2026-06-15', endDate: '2026-09-15' }],
  preloadHolidays: true,
}

export function toPeriodFormValues(period: PeriodDto): PeriodFormValues {
  return {
    name: period.name,
    startDate: period.startDate,
    endDate: period.endDate,
    agreementMinutes: formatMinutes(period.agreementMinutes),
    vacationDays: String(period.vacationDays),
    normalDayMinutes: formatMinutes(period.normalDayMinutes),
    intensiveDayMinutes: formatMinutes(period.intensiveDayMinutes),
    breakfastToleranceMin: String(period.breakfastToleranceMin),
    minLunchMin: String(period.minLunchMin),
    roundingStepMin: String(period.roundingStepMin),
    maxRemotePct: String(period.maxRemotePct),
    maxRemoteDaysMonth: String(period.maxRemoteDaysMonth),
    openingBalanceMin: formatMinutes(period.openingBalanceMin),
    intensiveRanges: period.intensiveRanges.map((r) => ({ ...r })),
    preloadHolidays: true,
  }
}

/**
 * Valores para un periodo nuevo: los del Excel si es el primero; si no, empieza el día después del
 * último, dura un año y copia sus parámetros (con la intensiva desplazada un año).
 */
export function newPeriodDefaults(periods: PeriodDto[]): PeriodFormValues {
  if (periods.length === 0) {
    return { ...EXCEL_DEFAULTS, intensiveRanges: EXCEL_DEFAULTS.intensiveRanges.map((r) => ({ ...r })) }
  }
  const last = [...periods].sort((a, b) => b.endDate.localeCompare(a.endDate))[0]
  const startDate = addDaysIso(last.endDate, 1)
  const endDate = oneYearPeriodEnd(startDate)
  const year = Number(startDate.slice(0, 4))
  const ranges = last.intensiveRanges
    .map((r) => ({ startDate: shiftYears(r.startDate, 1), endDate: shiftYears(r.endDate, 1) }))
    .filter((r) => r.startDate >= startDate && r.endDate <= endDate)
  return {
    ...toPeriodFormValues(last),
    name: `${year}-${year + 1}`,
    startDate,
    endDate,
    openingBalanceMin: '0:00',
    intensiveRanges: ranges,
    preloadHolidays: true,
  }
}

function intIn(min: number, max: number) {
  return z
    .string()
    .trim()
    .regex(/^\d+$/, 'Escribe un número entero')
    .refine((v) => Number(v) >= min && Number(v) <= max, `Debe estar entre ${min} y ${max}`)
}

function duration(min: number, max: number, message: string) {
  return z
    .string()
    .trim()
    .refine((v) => parseDuration(v) !== null, 'Formato h:mm')
    .refine((v) => {
      const minutes = parseDuration(v)
      return minutes === null || (minutes >= min && minutes <= max)
    }, message)
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

export const periodSchema = z
  .object({
    name: z.string().trim().min(1, 'Indica un nombre').max(100, 'Máximo 100 caracteres'),
    startDate: z.string().regex(ISO_DATE, 'Fecha no válida'),
    endDate: z.string().regex(ISO_DATE, 'Fecha no válida'),
    agreementMinutes: duration(1, 10_000 * 60, 'Las horas de convenio deben ser mayores que 0'),
    vacationDays: intIn(0, 366),
    normalDayMinutes: duration(1, 1440, 'La jornada debe estar entre 0:01 y 24:00'),
    intensiveDayMinutes: duration(1, 1440, 'La jornada debe estar entre 0:01 y 24:00'),
    breakfastToleranceMin: intIn(0, 240),
    minLunchMin: intIn(0, 240),
    roundingStepMin: intIn(1, 60),
    maxRemotePct: intIn(0, 100),
    maxRemoteDaysMonth: intIn(0, 31),
    openingBalanceMin: z
      .string()
      .trim()
      .refine((v) => parseDuration(v) !== null, 'Formato h:mm (puede ser negativo, p. ej. -1:30)'),
    intensiveRanges: z.array(
      z.object({
        startDate: z.string().regex(ISO_DATE, 'Fecha no válida'),
        endDate: z.string().regex(ISO_DATE, 'Fecha no válida'),
      }),
    ),
    preloadHolidays: z.boolean(),
  })
  .superRefine((values, ctx) => {
    const datesOk = ISO_DATE.test(values.startDate) && ISO_DATE.test(values.endDate)
    if (datesOk && values.startDate >= values.endDate) {
      ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'El fin debe ser posterior al inicio' })
    }
    const ranges = values.intensiveRanges
      .map((r, index) => ({ ...r, index }))
      .filter((r) => ISO_DATE.test(r.startDate) && ISO_DATE.test(r.endDate))
    for (const r of ranges) {
      if (r.startDate > r.endDate) {
        ctx.addIssue({
          code: 'custom',
          path: ['intensiveRanges', r.index, 'endDate'],
          message: 'El fin no puede ser anterior al inicio',
        })
      } else if (datesOk && (r.startDate < values.startDate || r.endDate > values.endDate)) {
        ctx.addIssue({
          code: 'custom',
          path: ['intensiveRanges', r.index, 'startDate'],
          message: 'El rango debe estar dentro del periodo',
        })
      }
    }
    const sorted = [...ranges].sort((a, b) => a.startDate.localeCompare(b.startDate))
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].startDate <= sorted[i - 1].endDate) {
        ctx.addIssue({
          code: 'custom',
          path: ['intensiveRanges', sorted[i].index, 'startDate'],
          message: 'Los rangos no pueden solaparse',
        })
      }
    }
  })

function toBody(values: PeriodFormValues): Omit<PeriodDto, 'id' | 'version'> {
  return {
    name: values.name.trim(),
    startDate: values.startDate,
    endDate: values.endDate,
    agreementMinutes: parseDuration(values.agreementMinutes) ?? 0,
    vacationDays: Number(values.vacationDays),
    normalDayMinutes: parseDuration(values.normalDayMinutes) ?? 0,
    intensiveDayMinutes: parseDuration(values.intensiveDayMinutes) ?? 0,
    breakfastToleranceMin: Number(values.breakfastToleranceMin),
    minLunchMin: Number(values.minLunchMin),
    roundingStepMin: Number(values.roundingStepMin),
    maxRemotePct: Number(values.maxRemotePct),
    maxRemoteDaysMonth: Number(values.maxRemoteDaysMonth),
    openingBalanceMin: parseDuration(values.openingBalanceMin) ?? 0,
    intensiveRanges: values.intensiveRanges.map((r) => ({ startDate: r.startDate, endDate: r.endDate })),
  }
}

export function toCreateRequest(values: PeriodFormValues): CreatePeriodRequest {
  return { ...toBody(values), preloadHolidays: values.preloadHolidays }
}

export function toUpdateRequest(values: PeriodFormValues, version: number): UpdatePeriodRequest {
  return { ...toBody(values), version }
}
