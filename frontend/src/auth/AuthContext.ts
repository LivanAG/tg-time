import { createContext, useContext } from 'react'

import type { UserDto } from '../api/types'

export interface AuthContextValue {
  /** "loading" mientras se intenta recuperar la sesión con la cookie de refresco al cargar la app. */
  status: 'loading' | 'ready'
  user: UserDto | null
  /** Access token en memoria (nunca en localStorage). */
  accessToken: string | null
  /** Mensaje para la pantalla de login (sesión caducada, contraseña cambiada...). */
  notice: string | null
  login: (email: string, password: string) => Promise<void>
  /** Cierra la sesión en el servidor y en memoria. */
  logout: (notice?: string) => Promise<void>
  updateUser: (user: UserDto) => void
  clearNotice: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  }
  return value
}
