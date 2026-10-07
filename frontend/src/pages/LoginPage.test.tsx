import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { getAccessToken } from '../api/client'
import { authResponse, dashboard, user } from '../test/fixtures'
import { json, mockApi, noContent, problem } from '../test/fetchMock'
import { renderApp } from '../test/renderApp'

function fillLogin(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('Login', () => {
  it('valida el formulario antes de llamar a la API', async () => {
    const { callsTo } = mockApi({ 'POST /api/auth/refresh': () => problem(401, 'Sin sesión') })
    renderApp('/login')

    await screen.findByRole('button', { name: 'Entrar' })
    fillLogin('no-es-un-email', '')

    expect(await screen.findByText('Email no válido')).toBeInTheDocument()
    expect(screen.getByText('Indica tu contraseña')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(callsTo('POST', '/api/auth/login')).toHaveLength(0)
  })

  it('con credenciales incorrectas muestra un mensaje genérico', async () => {
    mockApi({
      'POST /api/auth/refresh': () => problem(401, 'Sin sesión'),
      'POST /api/auth/login': () => problem(401, 'Email o contraseña incorrectos'),
    })
    renderApp('/login')

    await screen.findByRole('button', { name: 'Entrar' })
    fillLogin('livan@example.com', 'contraseña-equivocada')

    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contraseña incorrectos')
    expect(getAccessToken()).toBeNull()
  })

  it('tras iniciar sesión vuelve a la ruta pedida con el token en memoria', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': () => problem(401, 'Sin sesión'),
      'POST /api/auth/login': json(authResponse('token-login')),
      'GET /api/periods': [],
      'GET /actuator/health': { status: 'UP' },
    })
    renderApp('/ajustes?seccion=perfil')

    await screen.findByRole('button', { name: 'Entrar' })
    fillLogin('  livan@example.com ', 'una-contraseña-larga')

    expect(await screen.findByRole('heading', { name: 'Perfil' })).toBeInTheDocument()
    expect(screen.getByLabelText('Nombre')).toHaveValue(user.name)
    expect(callsTo('POST', '/api/auth/login')[0].body).toEqual({
      email: 'livan@example.com',
      password: 'una-contraseña-larga',
    })
    expect(getAccessToken()).toBe('token-login')
  })

  it('cerrar sesión llama a /api/auth/logout y vuelve al login', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/summary/dashboard': dashboard(),
      'POST /api/auth/logout': () => noContent(),
      'GET /actuator/health': { status: 'UP' },
    })
    renderApp('/')

    fireEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    await waitFor(() => expect(callsTo('POST', '/api/auth/logout')).toHaveLength(1))
    expect(getAccessToken()).toBeNull()
  })
})
