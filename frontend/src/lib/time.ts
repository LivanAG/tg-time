// Horas y duraciones. Todo se trabaja en minutos enteros, como en el backend.

/** "HH:mm" o "H:mm" (hora del día, 00:00-23:59). */
export const TIME_PATTERN = /^([01]?\d|2[0-3]):([0-5]\d)$/

/** Duración "h:mm" con horas sin límite y signo opcional ("1760:00", "-1:30"). */
export const DURATION_PATTERN = /^([+-])?(\d{1,5}):([0-5]\d)$/

export interface FormatMinutesOptions {
  /** Muestra "+" en positivos (saldos y diferencias). Los negativos llevan siempre "-". */
  signed?: boolean
}

/**
 * Minutos → "h:mm". 90 → "1:30", -90 → "-1:30"; con signo, 45 → "+0:45". El cero nunca lleva signo.
 */
export function formatMinutes(minutes: number, options: FormatMinutesOptions = {}): string {
  const value = Math.trunc(minutes)
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : options.signed && value > 0 ? '+' : ''
  return `${sign}${Math.floor(abs / 60)}:${String(abs % 60).padStart(2, '0')}`
}

/** Igual que formatMinutes, con "—" para null/undefined. */
export function formatOptionalMinutes(minutes: number | null | undefined, options: FormatMinutesOptions = {}): string {
  return minutes === null || minutes === undefined ? '—' : formatMinutes(minutes, options)
}

/** Hora "HH:mm" → minutos desde las 00:00; null si está vacía o no es válida. */
export function parseTime(value: string | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  const match = TIME_PATTERN.exec(value.trim())
  if (!match) {
    return null
  }
  return Number(match[1]) * 60 + Number(match[2])
}

/** Minutos desde las 00:00 → "HH:mm". */
export function minutesToTime(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** Normaliza "7:05" → "07:05"; deja tal cual lo que no sea una hora válida. */
export function normalizeTime(value: string): string {
  const minutes = parseTime(value)
  return minutes === null ? value : minutesToTime(minutes)
}

/** Duración "h:mm" (con signo opcional) → minutos; null si está vacía o no es válida. */
export function parseDuration(value: string | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  const match = DURATION_PATTERN.exec(value.trim())
  if (!match) {
    return null
  }
  const minutes = Number(match[2]) * 60 + Number(match[3])
  return match[1] === '-' ? -minutes : minutes
}

/** Minutos → texto para un campo de duración ("" si es null). */
export function durationInputValue(minutes: number | null | undefined): string {
  return minutes === null || minutes === undefined ? '' : formatMinutes(minutes)
}
