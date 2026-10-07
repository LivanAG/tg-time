import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'

import { periodsApi } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { IsoDate, PeriodDto } from '../api/types'
import { todayIso } from '../lib/dates'

export function usePeriods() {
  return useQuery({ queryKey: queryKeys.periods, queryFn: periodsApi.list })
}

/** Periodo que contiene la fecha, o undefined. */
export function findPeriodForDate(periods: PeriodDto[] | undefined, date: IsoDate): PeriodDto | undefined {
  return periods?.find((p) => p.startDate <= date && date <= p.endDate)
}

/** Periodo por defecto: el que contiene hoy; si no hay, el más reciente (la API los da ordenados). */
export function defaultPeriod(periods: PeriodDto[] | undefined, today: IsoDate = todayIso()): PeriodDto | undefined {
  return findPeriodForDate(periods, today) ?? periods?.[0]
}

/** Periodo elegido en la URL (?periodo=id) o el periodo por defecto. */
export function useSelectedPeriod() {
  const periodsQuery = usePeriods()
  const [searchParams, setSearchParams] = useSearchParams()
  const requested = searchParams.get('periodo')
  const periods = periodsQuery.data
  const period = periods?.find((p) => p.id === requested) ?? defaultPeriod(periods)

  const selectPeriod = (id: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('periodo', id)
    setSearchParams(next, { replace: true })
  }

  return { periodsQuery, periods, period, selectPeriod }
}
