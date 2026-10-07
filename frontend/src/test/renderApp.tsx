import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'

import App from '../App'

export function testQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
}

/** Pinta la app completa en una ruta (la sesión se recupera con el POST /api/auth/refresh simulado). */
export function renderApp(path = '/') {
  const client = testQueryClient()
  const result = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { client, ...result }
}

/** Pinta un componente suelto con QueryClient y router. */
export function renderWithProviders(ui: ReactElement, path = '/') {
  const client = testQueryClient()
  const result = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
  return { client, ...result }
}
