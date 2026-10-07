const FALLBACK = ['Europe/Madrid', 'Atlantic/Canary', 'Europe/Lisbon', 'Europe/London', 'UTC']

/** Zonas horarias IANA disponibles en el navegador, con la actual incluida. */
export function timeZones(current?: string): string[] {
  let zones: string[]
  try {
    zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : FALLBACK
  } catch {
    zones = FALLBACK
  }
  const all = new Set(zones.length > 0 ? zones : FALLBACK)
  all.add('Europe/Madrid')
  if (current) {
    all.add(current)
  }
  return [...all].sort()
}

export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Madrid'
  } catch {
    return 'Europe/Madrid'
  }
}
