import { z } from 'zod'

/**
 * Misma política que el backend: 12-128 caracteres y distinta del email. La lista de contraseñas
 * comunes solo la comprueba el backend (su error llega en errors[] y se pinta junto al campo).
 */
export function passwordSchema(email: () => string) {
  return z
    .string()
    .min(12, 'Mínimo 12 caracteres')
    .max(128, 'Máximo 128 caracteres')
    .refine(
      (value) => value.trim().toLowerCase() !== email().trim().toLowerCase(),
      'La contraseña no puede ser el email',
    )
}
