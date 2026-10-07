import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, type Location } from 'react-router'
import { z } from 'zod'

import { isApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ApiStatus } from '../components/ApiStatus'
import { FullScreenLoader } from '../components/FullScreenLoader'
import { Alert, Button, TextField } from '../components/ui'

const loginSchema = z.object({
  email: z.string().trim().min(1, 'Indica tu email').email('Email no válido'),
  password: z.string().min(1, 'Indica tu contraseña'),
})

type LoginValues = z.infer<typeof loginSchema>

/** Mensaje genérico: nunca se revela si el email existe. */
function loginErrorMessage(error: unknown): string {
  if (isApiError(error)) {
    if (error.status === 401) {
      return 'Email o contraseña incorrectos'
    }
    if (error.status === 429) {
      return error.retryAfterSeconds
        ? `Demasiados intentos. Espera ${error.retryAfterSeconds} s y vuelve a probar.`
        : 'Demasiados intentos. Espera un minuto y vuelve a probar.'
    }
    if (error.isNetworkError) {
      return error.detail
    }
  }
  return 'No se ha podido iniciar sesión. Inténtalo de nuevo.'
}

/** Ruta a la que volver tras el login (la que se pidió antes de que hiciera falta iniciar sesión). */
function redirectTarget(state: unknown): string {
  const from = (state as { from?: Location } | null)?.from
  if (!from || from.pathname === '/login') {
    return '/'
  }
  return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
}

export function LoginPage() {
  const { status, user, login, notice } = useAuth()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

  if (status === 'loading') {
    return <FullScreenLoader />
  }
  if (user) {
    return <Navigate to={redirectTarget(location.state)} replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      // Con la sesión iniciada se vuelve a pintar y <Navigate> lleva a la ruta pedida.
      await login(values.email, values.password)
    } catch (e) {
      setError(loginErrorMessage(e))
    }
  })

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-sm">
        <h1 className="mb-1 text-center text-2xl font-semibold text-slate-900">Control Horario</h1>
        <p className="mb-6 text-center text-sm text-slate-600">Inicia sesión para registrar tu jornada</p>
        <form
          onSubmit={onSubmit}
          noValidate
          className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
          aria-label="Iniciar sesión"
        >
          {notice && !error && <Alert tone="info">{notice}</Alert>}
          {error && <Alert tone="error">{error}</Alert>}
          <TextField
            label="Email"
            type="email"
            autoComplete="username"
            inputMode="email"
            error={errors.email?.message}
            {...register('email')}
          />
          <TextField
            label="Contraseña"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" size="lg" className="w-full" busy={isSubmitting}>
            Entrar
          </Button>
        </form>
        <div className="mt-6 flex justify-center">
          <ApiStatus />
        </div>
      </div>
    </div>
  )
}
