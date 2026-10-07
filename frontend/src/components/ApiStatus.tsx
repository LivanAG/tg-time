import { useQuery } from '@tanstack/react-query'

import { fetchHealth, healthQueryKey } from '../api/health'

const styles = {
  pending: { label: 'Conectando con la API…', dot: 'bg-amber-400' },
  up: { label: 'API operativa', dot: 'bg-emerald-500' },
  down: { label: 'API no disponible', dot: 'bg-red-500' },
}

export function ApiStatus() {
  const { data, isPending, isError } = useQuery({ queryKey: healthQueryKey, queryFn: fetchHealth })

  const state = isPending ? styles.pending : !isError && data === 'UP' ? styles.up : styles.down

  return (
    <span role="status" className="inline-flex items-center gap-2 text-sm text-slate-600">
      <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${state.dot}`} />
      {state.label}
    </span>
  )
}
