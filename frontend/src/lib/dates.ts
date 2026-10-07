import { addDays, addMonths, addYears, format, getISODay, isValid, parse, parseISO, subDays } from 'date-fns'
import { es } from 'date-fns/locale'

import type { IsoDate, YearMonth } from '../api/types'

const MONTH_PATTERN = /^(\d{4})-(0[1-9]|1[0-2])$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** Fecha local de hoy "YYYY-MM-DD" (zona del navegador). */
export function todayIso(now: Date = new Date()): IsoDate {
  return format(now, 'yyyy-MM-dd')
}

export function isIsoDate(value: string): boolean {
  return DATE_PATTERN.test(value) && isValid(parseISO(value))
}

export function isYearMonth(value: string | undefined): value is YearMonth {
  return value !== undefined && MONTH_PATTERN.test(value)
}

export function monthOf(date: IsoDate): YearMonth {
  return date.slice(0, 7)
}

export function splitMonth(month: YearMonth): { year: number; month: number } {
  const [year, m] = month.split('-').map(Number)
  return { year, month: m }
}

export function shiftMonth(month: YearMonth, delta: number): YearMonth {
  return format(addMonths(parse(`${month}-01`, 'yyyy-MM-dd', new Date()), delta), 'yyyy-MM')
}

export function addDaysIso(date: IsoDate, days: number): IsoDate {
  return format(addDays(parseISO(date), days), 'yyyy-MM-dd')
}

/** Mismo día un año después menos uno (fin de un periodo anual que empieza en `start`). */
export function oneYearPeriodEnd(start: IsoDate): IsoDate {
  return format(subDays(addYears(parseISO(start), 1), 1), 'yyyy-MM-dd')
}

export function shiftYears(date: IsoDate, years: number): IsoDate {
  return format(addYears(parseISO(date), years), 'yyyy-MM-dd')
}

/** "junio de 2026" */
export function formatMonthLong(month: YearMonth): string {
  return format(parseISO(`${month}-01`), "MMMM 'de' yyyy", { locale: es })
}

/** "jun 2026" */
export function formatMonthShort(month: YearMonth): string {
  return format(parseISO(`${month}-01`), 'MMM yyyy', { locale: es })
}

/** "miércoles, 7 de octubre de 2026" */
export function formatDateLong(date: IsoDate): string {
  return format(parseISO(date), "EEEE, d 'de' MMMM 'de' yyyy", { locale: es })
}

/** "mié 7" */
export function formatDayShort(date: IsoDate): string {
  return format(parseISO(date), 'EEE d', { locale: es })
}

/** "07/10/2026" */
export function formatDate(date: IsoDate): string {
  return format(parseISO(date), 'dd/MM/yyyy')
}

/** "7 oct" */
export function formatDayMonth(date: IsoDate): string {
  return format(parseISO(date), 'd MMM', { locale: es })
}

/** 1 = lunes ... 7 = domingo. */
export function isoWeekday(date: IsoDate): number {
  return getISODay(parseISO(date))
}

export function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
