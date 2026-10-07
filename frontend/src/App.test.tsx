import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import App from './App'

function renderApp(path = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function stubHealth(response: Response) {
  const fetchMock = vi.fn(async () => response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('App', () => {
  it('muestra la portada y que la API está operativa', async () => {
    const fetchMock = stubHealth(Response.json({ status: 'UP' }))

    renderApp()

    expect(screen.getByRole('heading', { name: 'Control Horario' })).toBeInTheDocument()
    expect(await screen.findByText('API operativa')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/actuator/health', expect.anything())
  })

  it('avisa si la API no responde', async () => {
    stubHealth(new Response(null, { status: 502 }))

    renderApp()

    expect(await screen.findByText('API no disponible')).toBeInTheDocument()
  })

  it('muestra una página de no encontrado para rutas desconocidas', () => {
    stubHealth(Response.json({ status: 'UP' }))

    renderApp('/no-existe')

    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
  })
})
