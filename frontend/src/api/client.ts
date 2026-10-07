import type { AuthResponse, FieldErrorDto, ProblemDetail, UserDto } from './types'

/** Todo cuelga de /api en el mismo origen (Vite en local, nginx en producción). */
export const API_BASE = '/api'

const DEFAULT_MESSAGES: Record<number, string> = {
  0: 'No se puede conectar con el servidor. Comprueba tu conexión.',
  400: 'Datos no válidos',
  401: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
  403: 'No tienes permiso para esta operación',
  404: 'No encontrado',
  409: 'El dato ha cambiado o choca con otro ya existente',
  413: 'El fichero supera el tamaño máximo (2 MB)',
  429: 'Demasiados intentos. Espera un momento y vuelve a probarlo.',
}

function defaultMessage(status: number): string {
  return DEFAULT_MESSAGES[status] ?? (status >= 500 ? 'Error interno del servidor' : `Error ${status}`)
}

/** Error de la API construido a partir de un ProblemDetail (o de un fallo de red, con status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly detail: string
  readonly title: string | null
  readonly errors: FieldErrorDto[]
  readonly retryAfterSeconds: number | null

  constructor(
    status: number,
    detail?: string | null,
    options: { title?: string | null; errors?: FieldErrorDto[]; retryAfterSeconds?: number | null } = {},
  ) {
    const message = detail && detail.trim() !== '' ? detail : defaultMessage(status)
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = message
    this.title = options.title ?? null
    this.errors = options.errors ?? []
    this.retryAfterSeconds = options.retryAfterSeconds ?? null
  }

  /** Mensaje de error de un campo concreto (p. ej. "endTime" o "breaks[1]"). */
  fieldError(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message
  }

  get isNetworkError(): boolean {
    return this.status === 0
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** Mensaje legible para cualquier error (ApiError, Error o desconocido). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.detail
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'Ha ocurrido un error inesperado'
}

// ---------------------------------------------------------------------------------------------
// Sesión en memoria: el access token nunca se guarda en localStorage ni en cookies accesibles.
// ---------------------------------------------------------------------------------------------

export interface Session {
  accessToken: string
  user: UserDto
}

let session: Session | null = null
const listeners = new Set<() => void>()
let sessionExpiredHandler: (() => void) | null = null
let refreshInFlight: Promise<Session | null> | null = null

export function getSession(): Session | null {
  return session
}

export function getAccessToken(): string | null {
  return session?.accessToken ?? null
}

export function setSession(next: Session | null): void {
  session = next
  listeners.forEach((listener) => listener())
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function sessionFromAuth(auth: AuthResponse): Session {
  return { accessToken: auth.accessToken, user: auth.user }
}

/** Lo llama el AuthProvider: qué hacer cuando la sesión no se puede renovar (ir a /login). */
export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

function expireSession(): void {
  setSession(null)
  sessionExpiredHandler?.()
}

/** Solo para tests: deja el módulo como recién cargado. */
export function resetClientState(): void {
  session = null
  refreshInFlight = null
  sessionExpiredHandler = null
  listeners.clear()
}

/**
 * Renueva la sesión con la cookie de refresco. Un único refresco compartido (single-flight): el
 * refresh token rota en cada uso y reutilizar uno ya rotado revoca toda la familia, así que dos
 * refrescos en paralelo cerrarían la sesión. Devuelve null si no hay sesión que recuperar y lanza
 * ApiError(0) si no hay conexión.
 */
export function refreshSession(): Promise<Session | null> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(): Promise<Session | null> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new ApiError(0)
  }
  if (!response.ok) {
    setSession(null)
    return null
  }
  const auth = (await response.json()) as AuthResponse
  const next = sessionFromAuth(auth)
  setSession(next)
  return next
}

// ---------------------------------------------------------------------------------------------
// Peticiones
// ---------------------------------------------------------------------------------------------

export type QueryValue = string | number | boolean | null | undefined

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** Se envía como JSON, salvo FormData (multipart). */
  body?: unknown
  query?: Record<string, QueryValue>
  signal?: AbortSignal
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') {
        params.append(key, String(value))
      }
    }
  }
  const search = params.toString()
  return `${API_BASE}${path}${search ? `?${search}` : ''}`
}

function isAuthPath(path: string): boolean {
  return path.startsWith('/auth/')
}

async function send(url: string, path: string, options: RequestOptions, accept: string): Promise<Response> {
  const headers: Record<string, string> = { Accept: accept }
  let body: BodyInit | undefined
  if (options.body instanceof FormData) {
    body = options.body
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }
  const token = getAccessToken()
  // En /api/auth/* el backend ignora Authorization: no se envía.
  if (token && !isAuthPath(path)) {
    headers.Authorization = `Bearer ${token}`
  }
  try {
    return await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body,
      credentials: 'same-origin',
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0)
  }
}

/**
 * Hace la petición y, si responde 401 fuera de /api/auth/*, renueva la sesión una vez (refresco
 * compartido) y reintenta. Si no se puede renovar, cierra la sesión (el AuthProvider lleva a /login).
 */
async function sendWithRefresh(path: string, options: RequestOptions, accept: string): Promise<Response> {
  const url = buildUrl(path, options.query)
  const tokenUsed = getAccessToken()
  const response = await send(url, path, options, accept)
  if (response.status !== 401 || isAuthPath(path)) {
    return response
  }
  const current = getAccessToken()
  // Si otra petición ya renovó el token mientras tanto, basta con reintentar con el nuevo.
  const renewed = current !== null && current !== tokenUsed ? getSession() : await refreshSession()
  if (!renewed) {
    expireSession()
    throw new ApiError(401)
  }
  return send(url, path, options, accept)
}

export async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = response.headers.get('Retry-After')
  const retryAfterSeconds = retryAfter !== null && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null
  let problem: ProblemDetail = {}
  const contentType = response.headers.get('Content-Type') ?? ''
  if (contentType.includes('json')) {
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      problem = {}
    }
  }
  return new ApiError(response.status, problem.detail, {
    title: problem.title ?? null,
    errors: Array.isArray(problem.errors) ? problem.errors : [],
    retryAfterSeconds,
  })
}

/** Petición JSON. Devuelve undefined en 204 o cuerpo vacío. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await sendWithRefresh(path, options, 'application/json')
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  return (text === '' ? undefined : JSON.parse(text)) as T
}

/** Igual que apiRequest pero convierte un 404 en null (recurso opcional). */
export async function apiRequestOrNull<T>(path: string, options: RequestOptions = {}): Promise<T | null> {
  try {
    return await apiRequest<T>(path, options)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export interface DownloadedFile {
  blob: Blob
  fileName: string
}

/** Descarga binaria autenticada (exportación a Excel). */
export async function apiDownload(
  path: string,
  query: Record<string, QueryValue>,
  fallbackName: string,
): Promise<DownloadedFile> {
  const response = await sendWithRefresh(path, { query }, '*/*')
  if (!response.ok) {
    throw await toApiError(response)
  }
  const blob = await response.blob()
  return { blob, fileName: fileNameFromDisposition(response.headers.get('Content-Disposition')) ?? fallbackName }
}

export function fileNameFromDisposition(header: string | null): string | null {
  if (!header) {
    return null
  }
  const encoded = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ''))
    } catch {
      // Se intenta con filename= a continuación.
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain ? plain[1].trim() : null
}

/** Guarda un blob como fichero en el navegador. */
export function saveBlob({ blob, fileName }: DownloadedFile): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
