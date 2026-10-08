import { describe, expect, it, vi } from 'vitest'

import { authResponse, period } from '../test/fixtures'
import { json, mockApi, noContent, problem } from '../test/fetchMock'
import {
  ApiError,
  apiRequest,
  apiRequestOrNull,
  fileNameFromDisposition,
  getAccessToken,
  refreshSession,
  sessionFromAuth,
  setSession,
  setSessionExpiredHandler,
} from './client'
import { importExportApi, periodsApi, workdaysApi } from './endpoints'

describe('cliente de la API', () => {
  it('usa rutas relativas /api, credentials same-origin y el token en Authorization', async () => {
    setSession(sessionFromAuth(authResponse('abc')))
    const { calls } = mockApi({ 'GET /api/periods': [period] })

    const periods = await periodsApi.list()

    expect(periods).toEqual([period])
    expect(calls[0].url.href).toBe('http://localhost/api/periods')
    expect(calls[0].credentials).toBe('same-origin')
    expect(calls[0].headers.get('Authorization')).toBe('Bearer abc')
  })

  it('tras un 401 hace UN único refresco compartido y reintenta cada petición con el token nuevo', async () => {
    setSession(sessionFromAuth(authResponse('caducado')))
    let refreshes = 0
    let releaseRefresh: () => void = () => undefined
    const refreshGate = new Promise<void>((resolve) => {
      releaseRefresh = resolve
    })
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': async () => {
        refreshes++
        await refreshGate
        return json(authResponse('nuevo'))
      },
      'GET /api/periods': (req) =>
        req.headers.get('Authorization') === 'Bearer nuevo' ? json([period]) : problem(401, 'Token caducado'),
      'GET /api/summary/dashboard': (req) =>
        req.headers.get('Authorization') === 'Bearer nuevo'
          ? json({ today: '2026-10-07' })
          : problem(401, 'Token caducado'),
      'GET /api/workdays/2026-10-07': (req) =>
        req.headers.get('Authorization') === 'Bearer nuevo' ? noContent() : problem(401, 'Token caducado'),
    })

    const pending = Promise.all([
      periodsApi.list(),
      apiRequest('/summary/dashboard'),
      apiRequest('/workdays/2026-10-07'),
    ])
    // Las tres peticiones fallan con 401 y esperan al mismo refresco.
    await vi.waitFor(() => expect(callsTo('POST', '/api/auth/refresh')).toHaveLength(1))
    releaseRefresh()
    const [periods, dashboard] = await pending

    expect(refreshes).toBe(1)
    expect(periods).toEqual([period])
    expect(dashboard).toEqual({ today: '2026-10-07' })
    expect(getAccessToken()).toBe('nuevo')
    expect(callsTo('GET', '/api/periods')).toHaveLength(2)
    // El refresco usa solo la cookie: sin Authorization.
    expect(callsTo('POST', '/api/auth/refresh')[0].headers.get('Authorization')).toBeNull()
    expect(callsTo('POST', '/api/auth/refresh')[0].credentials).toBe('same-origin')
  })

  it('si el refresco falla, cierra la sesión, avisa al AuthProvider y lanza 401', async () => {
    setSession(sessionFromAuth(authResponse('caducado')))
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': () => problem(401, 'Sesión caducada'),
      'GET /api/periods': () => problem(401, 'Token caducado'),
    })

    await expect(periodsApi.list()).rejects.toMatchObject({ status: 401 })

    expect(expired).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
    expect(callsTo('GET', '/api/periods')).toHaveLength(1)
  })

  it('reintenta solo una vez: si vuelve a dar 401 con el token nuevo, devuelve el error sin cerrar la sesión', async () => {
    setSession(sessionFromAuth(authResponse('viejo')))
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse('nuevo')),
      'PUT /api/me/password': () => problem(401, 'La contraseña actual no es correcta'),
    })

    await expect(apiRequest('/me/password', { method: 'PUT', body: {} })).rejects.toMatchObject({
      status: 401,
      detail: 'La contraseña actual no es correcta',
    })
    expect(callsTo('PUT', '/api/me/password')).toHaveLength(2)
    expect(callsTo('POST', '/api/auth/refresh')).toHaveLength(1)
    expect(expired).not.toHaveBeenCalled()
  })

  it('un 401 en /api/auth/* no intenta refrescar', async () => {
    const { callsTo } = mockApi({ 'POST /api/auth/login': () => problem(401, 'Email o contraseña incorrectos') })

    await expect(
      apiRequest('/auth/login', { method: 'POST', body: { email: 'a@b.es', password: 'x' } }),
    ).rejects.toMatchObject({
      status: 401,
      detail: 'Email o contraseña incorrectos',
    })
    expect(callsTo('POST', '/api/auth/refresh')).toHaveLength(0)
    expect(callsTo('POST', '/api/auth/login')[0].headers.get('Authorization')).toBeNull()
  })

  it('convierte un ProblemDetail 400 en ApiError con los errores por campo', async () => {
    setSession(sessionFromAuth(authResponse()))
    mockApi({
      'PUT /api/workdays/2026-10-07': () =>
        problem(400, 'Datos no válidos', [{ field: 'endTime', message: 'La salida debe ser posterior a la entrada' }]),
    })

    const error = await workdaysApi
      .save('2026-10-07', 'period-1', {
        startTime: '17:00',
        endTime: '08:00',
        breaks: [],
        location: 'OFICINA',
        officeStart: null,
        officeEnd: null,
        homeStart: null,
        homeEnd: null,
        notes: null,
        version: null,
      })
      .catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.detail).toBe('Datos no válidos')
    expect(apiError.fieldError('endTime')).toBe('La salida debe ser posterior a la entrada')
  })

  it('lee Retry-After en los 429 y da un mensaje por defecto si no hay cuerpo', async () => {
    mockApi({
      'POST /api/auth/login': () => new Response(null, { status: 429, headers: { 'Retry-After': '42' } }),
    })
    const error = (await apiRequest('/auth/login', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError
    expect(error.status).toBe(429)
    expect(error.retryAfterSeconds).toBe(42)
    expect(error.detail).toMatch(/Demasiados intentos/)
  })

  it('un fallo de red es un ApiError con status 0', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))),
    )
    const error = (await apiRequest('/periods').catch((e: unknown) => e)) as ApiError
    expect(error.isNetworkError).toBe(true)
    expect(error.detail).toMatch(/No se puede conectar/)
  })

  it('apiRequestOrNull convierte un 404 en null', async () => {
    setSession(sessionFromAuth(authResponse()))
    mockApi({ 'GET /api/workdays/2026-10-08': () => problem(404, 'No existe') })
    await expect(apiRequestOrNull('/workdays/2026-10-08')).resolves.toBeNull()
  })

  it('refreshSession devuelve null si no hay cookie válida y comparte la petición en curso', async () => {
    const { callsTo } = mockApi({ 'POST /api/auth/refresh': () => problem(401, 'Sin sesión') })
    const [a, b] = await Promise.all([refreshSession(), refreshSession()])
    expect(a).toBeNull()
    expect(b).toBeNull()
    expect(callsTo('POST', '/api/auth/refresh')).toHaveLength(1)
  })

  it('la importación sube multipart con dryRun y las opciones', async () => {
    setSession(sessionFromAuth(authResponse()))
    const { calls } = mockApi({ 'POST /api/import/xlsx': { dryRun: true } })
    const file = new File(['PK'], 'HORAS.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await importExportApi.importXlsx(file, {
      dryRun: true,
      periodId: null,
      includeFuture: false,
      markVacations: true,
      overwrite: false,
    })

    const form = calls[0].body as FormData
    expect(form).toBeInstanceOf(FormData)
    expect((form.get('file') as File).name).toBe('HORAS.xlsx')
    expect(form.get('dryRun')).toBe('true')
    expect(form.get('markVacations')).toBe('true')
    expect(form.has('periodId')).toBe(false)
    // El navegador pone el boundary: no se fija Content-Type.
    expect(calls[0].headers.get('Content-Type')).toBeNull()
  })

  it('saca el nombre del fichero de Content-Disposition', () => {
    expect(fileNameFromDisposition('attachment; filename="horas-2026-06.xlsx"')).toBe('horas-2026-06.xlsx')
    expect(fileNameFromDisposition("attachment; filename*=UTF-8''horas%202026.xlsx")).toBe('horas 2026.xlsx')
    expect(fileNameFromDisposition(null)).toBeNull()
  })
})
