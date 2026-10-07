export const healthQueryKey = ['health'] as const

/** Estado del backend tal como lo publica Spring Boot Actuator ("UP", "DOWN"...). */
export async function fetchHealth(): Promise<string> {
  const response = await fetch('/actuator/health', { headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new Error(`La API respondió ${response.status}`)
  }
  const body = (await response.json()) as { status?: string }
  return body.status ?? 'UNKNOWN'
}
