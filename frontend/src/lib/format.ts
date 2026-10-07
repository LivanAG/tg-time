import type { AbsenceType, BreakType, HolidayScope, ImportDayStatus, Role, WorkLocation } from '../api/types'

const decimal = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 })

/** 12.5 → "12,5"; 3 → "3". */
export function formatNumber(value: number): string {
  return decimal.format(value)
}

/** 45.6 → "45,6 %". */
export function formatPct(value: number): string {
  return `${decimal.format(value)} %`
}

/** 1 → "1 día"; 12.5 → "12,5 días". */
export function formatDays(value: number): string {
  return `${decimal.format(value)} ${value === 1 ? 'día' : 'días'}`
}

export const LOCATION_LABELS: Record<WorkLocation, string> = {
  OFICINA: 'Oficina',
  CASA: 'Casa',
  MIXTO: 'Mixto',
}

export const BREAK_LABELS: Record<BreakType, string> = {
  DESAYUNO: 'Desayuno',
  COMIDA: 'Comida',
  OTRA: 'Otra pausa',
}

export const BREAK_SHORT: Record<BreakType, string> = {
  DESAYUNO: 'D',
  COMIDA: 'C',
  OTRA: 'O',
}

export const ABSENCE_LABELS: Record<AbsenceType, string> = {
  VACACIONES: 'Vacaciones',
  PUENTE: 'Puente recuperable',
  PERMISO: 'Permiso retribuido',
  BAJA: 'Baja',
}

export const HOLIDAY_SCOPE_LABELS: Record<HolidayScope, string> = {
  NACIONAL: 'Nacional',
  AUTONOMICO: 'Autonómico',
  LOCAL: 'Local',
  EMPRESA: 'Empresa',
}

export const ROLE_LABELS: Record<Role, string> = {
  USER: 'Usuario',
  ADMIN: 'Administrador',
}

export const IMPORT_STATUS_LABELS: Record<ImportDayStatus, string> = {
  NEW: 'Nuevo',
  EXISTS: 'Ya existe',
  FUTURE: 'Futuro',
  OUT_OF_PERIOD: 'Fuera del periodo',
  INVALID: 'No válido',
  NOT_REPRESENTABLE: 'No representable',
}

export function absenceLabel(type: AbsenceType, halfDay: boolean): string {
  return halfDay ? `${ABSENCE_LABELS[type]} (medio día)` : ABSENCE_LABELS[type]
}
