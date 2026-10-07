import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { ApiError, errorMessage } from '../api/client'

/** Campo de la API → ruta de React Hook Form: "breaks[1].startTime" → "breaks.1.startTime". */
export function apiFieldToPath(field: string): string {
  return field.replace(/\[(\d+)\]/g, '.$1')
}

/**
 * Pinta junto a cada campo los errores de un 400 (errors[] del ProblemDetail). Devuelve los mensajes
 * que no corresponden a ningún campo del formulario, para mostrarlos arriba. `fields` son las rutas
 * raíz que tiene el formulario (p. ej. ["startTime", "breaks"]).
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly string[],
): string[] {
  if (!(error instanceof ApiError)) {
    return [errorMessage(error)]
  }
  if (error.errors.length === 0) {
    return [error.detail]
  }
  const unmatched: string[] = []
  error.errors.forEach((fieldError, index) => {
    const path = apiFieldToPath(fieldError.field ?? '')
    const known = fields.some((f) => path === f || path.startsWith(`${f}.`))
    if (known) {
      setError(path as Path<T>, { type: 'server', message: fieldError.message }, { shouldFocus: index === 0 })
    } else {
      unmatched.push(fieldError.message)
    }
  })
  return unmatched
}
