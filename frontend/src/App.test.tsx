import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { authResponse, dashboard } from './test/fixtures'
import { json, mockApi, problem } from './test/fetchMock'
import { renderApp } from './test/renderApp'

describe('App', () => {
  it('muestra una pantalla de carga mientras recupera la sesión', async () => {
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    mockApi({
      'POST /api/auth/refresh': async () => {
        await gate
        return json(authResponse())
      },
      'GET /api/summary/dashboard': dashboard(),
    })

    renderApp('/')

    expect(screen.getByRole('status')).toHaveTextContent('Recuperando la sesión')
    release()
    expect(await screen.findByText('Saldo acumulado hasta hoy')).toBeInTheDocument()
  })

  it('sin sesión lleva a /login y comprueba la API', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': () => problem(401, 'Sin sesión'),
      'GET /actuator/health': { status: 'UP' },
    })

    renderApp('/calendario')

    expect(await screen.findByRole('heading', { name: 'Control Horario' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(await screen.findByText('API operativa')).toBeInTheDocument()
    expect(callsTo('POST', '/api/auth/refresh')).toHaveLength(1)
  })

  it('muestra una página de no encontrado para rutas desconocidas', async () => {
    mockApi({ 'POST /api/auth/refresh': json(authResponse()) })

    renderApp('/no-existe')

    expect(await screen.findByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
  })

  it('la navegación principal enlaza las cinco pantallas', async () => {
    mockApi({ 'POST /api/auth/refresh': json(authResponse()), 'GET /api/summary/dashboard': dashboard() })

    renderApp('/')

    const nav = await screen.findByRole('navigation', { name: 'Principal' })
    const links = Array.from(nav.querySelectorAll('a')).map((a) => a.textContent)
    expect(links).toEqual(['Inicio', 'Registro', 'Calendario', 'Resumen', 'Ajustes'])
  })
})
