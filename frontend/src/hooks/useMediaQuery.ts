import { useSyncExternalStore } from 'react'

/** Escritorio a partir de 768 px (breakpoint md de Tailwind). */
export const DESKTOP_QUERY = '(min-width: 768px)'

/**
 * Estado de una media query. Sin matchMedia (pruebas, navegadores antiguos) devuelve `fallback`.
 * Se usa para pintar solo una de las dos vistas (tabla o tarjetas) en lugar de ocultarla con CSS.
 */
export function useMediaQuery(query: string, fallback = true): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof window.matchMedia !== 'function') {
        return () => undefined
      }
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    () => (typeof window.matchMedia === 'function' ? window.matchMedia(query).matches : fallback),
    () => fallback,
  )
}

export function useIsDesktop(): boolean {
  return useMediaQuery(DESKTOP_QUERY, true)
}
