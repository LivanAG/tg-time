import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { periodsApi } from '../api/endpoints'
import { invalidatePeriodData, queryKeys } from '../api/queryKeys'
import type { IsoDate, PeriodDto, YearMonth } from '../api/types'
import { monthOf, todayIso } from '../lib/dates'

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

/** Periodo con el que trabaja la app: el que la API marca como seleccionado (o, por si acaso, el por defecto). */
export function selectedPeriod(periods: PeriodDto[] | undefined): PeriodDto | undefined {
  return periods?.find((p) => p.selected) ?? defaultPeriod(periods)
}

/** Primer y último mes (YYYY-MM) que toca el periodo. */
export function periodMonths(period: PeriodDto): { first: YearMonth; last: YearMonth } {
  return { first: monthOf(period.startDate), last: monthOf(period.endDate) }
}

/** Mes de referencia del periodo: el de hoy si cae dentro; si no, su primer o su último mes. */
export function referenceMonth(period: PeriodDto, today: IsoDate = todayIso()): YearMonth {
  const { first, last } = periodMonths(period)
  const current = monthOf(today)
  if (current < first) {
    return first
  }
  return current > last ? last : current
}

/** El mes, llevado al rango de meses del periodo. */
export function clampMonth(period: PeriodDto, month: YearMonth): YearMonth {
  const { first, last } = periodMonths(period)
  if (month < first) {
    return first
  }
  return month > last ? last : month
}

/**
 * Periodo seleccionado (guardado en la cuenta, el mismo en todos los dispositivos) y la forma de
 * cambiarlo. Al cambiarlo se recarga todo lo que depende del periodo.
 */
export function useSelectedPeriod() {
  const queryClient = useQueryClient()
  const periodsQuery = usePeriods()
  const periods = periodsQuery.data
  const period = selectedPeriod(periods)

  const select = useMutation({
    mutationFn: (id: string) => periodsApi.select(id),
    onMutate: (id) => {
      // Cambio inmediato en pantalla; si la API falla, la recarga de onSettled lo deshace.
      queryClient.setQueryData<PeriodDto[]>(queryKeys.periods, (old) =>
        old?.map((p) => ({ ...p, selected: p.id === id })),
      )
    },
    onSettled: () => invalidatePeriodData(queryClient),
  })

  const selectPeriod = (id: string) => {
    if (id !== period?.id) {
      select.mutate(id)
    }
  }

  return { periodsQuery, periods, period, selectPeriod, selectError: select.error }
}
