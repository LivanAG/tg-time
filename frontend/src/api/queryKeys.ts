import type { QueryClient } from '@tanstack/react-query'

import type { IsoDate, YearMonth } from './types'

/** Claves de TanStack Query. El primer elemento agrupa lo que se invalida junto. */
export const queryKeys = {
  dashboard: ['dashboard'] as const,
  periods: ['periods'] as const,
  holidays: (periodId: string) => ['holidays', periodId] as const,
  calendar: (periodId: string) => ['calendar', periodId] as const,
  month: (month: YearMonth, periodId: string) => ['month', month, periodId] as const,
  periodSummary: (periodId: string) => ['period-summary', periodId] as const,
  workday: (date: IsoDate, periodId: string) => ['workday', date, periodId] as const,
  absence: (date: IsoDate, periodId: string) => ['absence', date, periodId] as const,
  adminUsers: ['admin-users'] as const,
}

/** Tras guardar o borrar un día o una ausencia: todo lo que se calcula a partir de ellos. */
export function invalidateDayData(queryClient: QueryClient): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    queryClient.invalidateQueries({ queryKey: ['month'] }),
    queryClient.invalidateQueries({ queryKey: ['period-summary'] }),
    queryClient.invalidateQueries({ queryKey: ['calendar'] }),
    queryClient.invalidateQueries({ queryKey: ['workday'] }),
    queryClient.invalidateQueries({ queryKey: ['absence'] }),
  ]).then(() => undefined)
}

/** Tras cambiar periodos, intensivas, festivos o importar: además, los periodos y festivos. */
export function invalidatePeriodData(queryClient: QueryClient): Promise<void> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['periods'] }),
    queryClient.invalidateQueries({ queryKey: ['holidays'] }),
    invalidateDayData(queryClient),
  ]).then(() => undefined)
}
