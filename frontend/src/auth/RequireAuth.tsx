import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { FullScreenLoader } from '../components/FullScreenLoader'
import { useAuth } from './AuthContext'

/** Rutas privadas: mientras se recupera la sesión, pantalla de carga; sin sesión, a /login. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, user } = useAuth()
  const location = useLocation()
  if (status === 'loading') {
    return <FullScreenLoader />
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <>{children}</>
}
