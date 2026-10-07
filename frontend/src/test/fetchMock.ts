import { vi } from 'vitest'

import type { FieldErrorDto } from '../api/types'

/** Petición recibida por el fetch simulado. */
export interface MockRequest {
  method: string
  path: string
  url: URL
  headers: Headers
  /** Cuerpo JSON ya parseado, o el FormData/valor tal cual. */
  body: unknown
  credentials: RequestCredentials | undefined
}

export type MockHandler = (request: MockRequest) => Response | Promise<Response>

/** Respuesta: función, Response (se clona en cada llamada) o cualquier valor que se devuelve como JSON 200. */
export type MockRoute = MockHandler | Response | object | null

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json', ...headers },
  })
}

export function problem(status: number, detail: string, errors?: FieldErrorDto[]): Response {
  return json({ type: 'about:blank', title: 'Error', status, detail, ...(errors ? { errors } : {}) }, status)
}

export function noContent(): Response {
  return new Response(null, { status: 204 })
}

/**
 * Sustituye fetch por un simulador de la API. Claves "MÉTODO /api/ruta" (opcionalmente con la query
 * exacta: "GET /api/summary/month?year=2026&month=10"). Lo que no está en `routes` responde 404.
 */
export function mockApi(routes: Record<string, MockRoute>) {
  const calls: MockRequest[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const url = new URL(raw, 'http://localhost')
    const method = (init?.method ?? 'GET').toUpperCase()
    let body: unknown = init?.body
    if (typeof init?.body === 'string') {
      try {
        body = JSON.parse(init.body)
      } catch {
        body = init.body
      }
    }
    const request: MockRequest = {
      method,
      path: url.pathname,
      url,
      headers: new Headers(init?.headers),
      body,
      credentials: init?.credentials,
    }
    calls.push(request)
    const route = routes[`${method} ${url.pathname}${url.search}`] ?? routes[`${method} ${url.pathname}`]
    if (route === undefined) {
      return problem(404, `Sin simulación para ${method} ${url.pathname}${url.search}`)
    }
    if (typeof route === 'function') {
      return (route as MockHandler)(request)
    }
    if (route instanceof Response) {
      // Una copia por llamada: la original no se consume y la ruta se puede repetir.
      return route.clone()
    }
    return json(route)
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    fetchMock,
    calls,
    callsTo: (method: string, path: string) => calls.filter((c) => c.method === method && c.path === path),
  }
}
