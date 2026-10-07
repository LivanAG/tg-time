import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'

import {
  getSession,
  refreshSession,
  sessionFromAuth,
  setSession,
  setSessionExpiredHandler,
  subscribeSession,
} from '../api/client'
import { authApi } from '../api/endpoints'
import type { UserDto } from '../api/types'
import { AuthContext, type AuthContextValue } from './AuthContext'

/**
 * Sesión: access token en memoria (variable del módulo cliente expuesta por este contexto) y refresh
 * token en cookie HttpOnly. Al cargar la app se intenta recuperar la sesión con POST /api/auth/refresh.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const session = useSyncExternalStore(subscribeSession, getSession, getSession)
  const [status, setStatus] = useState<'loading' | 'ready'>(() => (getSession() ? 'ready' : 'loading'))
  const [notice, setNotice] = useState<string | null>(null)
  const previousUserId = useRef<string | null>(session?.user.id ?? null)

  useEffect(() => {
    let active = true
    if (getSession()) {
      // Ya hay sesión en memoria (el estado inicial ya es "ready").
      return
    }
    // Con StrictMode el efecto se ejecuta dos veces: refreshSession comparte el mismo refresco.
    refreshSession()
      .catch(() => null)
      .finally(() => {
        if (active) {
          setStatus('ready')
        }
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    setSessionExpiredHandler(() => setNotice('Tu sesión ha caducado. Vuelve a iniciar sesión.'))
    return () => setSessionExpiredHandler(null)
  }, [])

  // Al cerrar sesión o cambiar de usuario no puede quedar nada en la caché del anterior.
  useEffect(() => {
    const id = session?.user.id ?? null
    if (previousUserId.current !== null && previousUserId.current !== id) {
      queryClient.clear()
    }
    previousUserId.current = id
  }, [session, queryClient])

  const login = useCallback(async (email: string, password: string) => {
    const auth = await authApi.login({ email, password })
    setNotice(null)
    setSession(sessionFromAuth(auth))
  }, [])

  const logout = useCallback(async (message?: string) => {
    try {
      await authApi.logout()
    } catch {
      // La sesión se cierra en el navegador aunque falle la llamada.
    } finally {
      setNotice(message ?? null)
      setSession(null)
    }
  }, [])

  const updateUser = useCallback((user: UserDto) => {
    const current = getSession()
    if (current) {
      setSession({ ...current, user })
    }
  }, [])

  const clearNotice = useCallback(() => setNotice(null), [])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user: session?.user ?? null,
      accessToken: session?.accessToken ?? null,
      notice,
      login,
      logout,
      updateUser,
      clearNotice,
    }),
    [status, session, notice, login, logout, updateUser, clearNotice],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
