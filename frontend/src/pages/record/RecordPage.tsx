import { useMutation, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'

import { errorMessage, isApiError, saveBlob } from '../../api/client'
import { importExportApi, summaryApi } from '../../api/endpoints'
import { queryKeys } from '../../api/queryKeys'
import type { PeriodDto } from '../../api/types'
import { buttonClasses } from '../../components/buttonClasses'
import { NoPeriod } from '../../components/NoPeriod'
import { Alert, Button, QueryError, Spinner } from '../../components/ui'
import { useDayEditor } from '../../features/workday/DayEditorContext'
import { useIsDesktop } from '../../hooks/useMediaQuery'
import { clampMonth, periodMonths, referenceMonth, useSelectedPeriod } from '../../hooks/usePeriods'
import { capitalize, formatMonthLong, isYearMonth, monthOf, shiftMonth, splitMonth, todayIso } from '../../lib/dates'
import type { CalcRules } from '../../lib/workdayCalc'
import { MonthCards } from './MonthCards'
import { MonthClose } from './MonthClose'
import { MonthTable } from './MonthTable'

/**
 * /registro/:month — hoja mensual del periodo seleccionado. Sin mes, o con un mes fuera del periodo,
 * lleva al mes que toca: el de hoy si cae dentro del periodo; si no, su primer o su último mes.
 */
export function RecordPage() {
  const { month } = useParams()
  const { periodsQuery, period } = useSelectedPeriod()
  if (periodsQuery.isPending) {
    return <Spinner label="Cargando el periodo…" />
  }
  if (periodsQuery.isError) {
    return <QueryError error={periodsQuery.error} onRetry={() => void periodsQuery.refetch()} />
  }
  if (!period) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-semibold text-slate-900">Registro</h1>
        <NoPeriod />
      </div>
    )
  }
  const target = isYearMonth(month) ? clampMonth(period, month) : referenceMonth(period)
  if (target !== month) {
    return <Navigate to={`/registro/${target}`} replace />
  }
  return <MonthRecord key={`${period.id}-${month}`} month={month} period={period} />
}

function MonthRecord({ month, period }: { month: string; period: PeriodDto }) {
  const { year, month: monthNumber } = splitMonth(month)
  const { openDay } = useDayEditor()
  const isDesktop = useIsDesktop()
  const [notice, setNotice] = useState<string | null>(null)
  const query = useQuery({
    queryKey: queryKeys.month(month, period.id),
    queryFn: () => summaryApi.month(year, monthNumber, period.id),
  })
  const exportMonth = useMutation({
    mutationFn: () => importExportApi.exportXlsx(year, monthNumber, period.id),
    onSuccess: saveBlob,
  })

  const { breakfastToleranceMin, minLunchMin } = period
  const rules = useMemo<CalcRules>(() => ({ breakfastToleranceMin, minLunchMin }), [breakfastToleranceMin, minLunchMin])
  const title = capitalize(formatMonthLong(month))
  const current = monthOf(todayIso())
  const { first, last } = periodMonths(period)
  const previous = month > first ? shiftMonth(month, -1) : null
  const next = month < last ? shiftMonth(month, 1) : null
  const currentInPeriod = first <= current && current <= last

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Registro</h1>
          <p className="text-sm text-slate-600">
            {title} · periodo {period.name}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <nav aria-label="Cambiar de mes" className="flex items-center gap-1">
            {previous ? (
              <Link to={`/registro/${previous}`} className={buttonClasses('secondary', 'sm')} aria-label="Mes anterior">
                ‹ <span className="hidden sm:inline">{capitalize(formatMonthLong(previous))}</span>
              </Link>
            ) : (
              <span
                className={buttonClasses('secondary', 'sm', 'cursor-not-allowed opacity-50')}
                aria-disabled="true"
                title="Primer mes del periodo"
              >
                ‹
              </span>
            )}
            {currentInPeriod && month !== current && (
              <Link to={`/registro/${current}`} className={buttonClasses('ghost', 'sm')}>
                Mes actual
              </Link>
            )}
            {next ? (
              <Link to={`/registro/${next}`} className={buttonClasses('secondary', 'sm')} aria-label="Mes siguiente">
                <span className="hidden sm:inline">{capitalize(formatMonthLong(next))}</span> ›
              </Link>
            ) : (
              <span
                className={buttonClasses('secondary', 'sm', 'cursor-not-allowed opacity-50')}
                aria-disabled="true"
                title="Último mes del periodo"
              >
                ›
              </span>
            )}
          </nav>
          <Button
            variant="secondary"
            size="sm"
            busy={exportMonth.isPending}
            onClick={() => exportMonth.mutate()}
            disabled={!query.data}
          >
            Exportar a Excel
          </Button>
        </div>
      </header>

      {exportMonth.isError && <Alert tone="error">No se ha podido exportar: {errorMessage(exportMonth.error)}</Alert>}
      {notice && (
        <Alert tone="warning">
          <div className="flex items-start justify-between gap-2">
            <span>{notice}</span>
            <button type="button" className="text-xs font-medium underline" onClick={() => setNotice(null)}>
              Cerrar
            </button>
          </div>
        </Alert>
      )}

      {query.isPending ? (
        <Spinner label="Cargando el mes…" />
      ) : query.isError ? (
        isApiError(query.error) && query.error.status === 404 ? (
          <Alert tone="info" title="Ningún periodo incluye este mes">
            Crea el periodo en{' '}
            <Link to="/ajustes?seccion=periodos" className="font-medium underline">
              Ajustes
            </Link>{' '}
            para fichar en {title.toLowerCase()}.
          </Alert>
        ) : (
          <QueryError error={query.error} onRetry={() => void query.refetch()} />
        )
      ) : (
        <>
          {query.data.status === 'FUTURE' && (
            <p className="text-sm text-slate-500">Mes futuro: las horas teóricas son una previsión.</p>
          )}
          {isDesktop ? (
            <MonthTable summary={query.data} rules={rules} onOpenDay={openDay} onNotice={setNotice} />
          ) : (
            <MonthCards summary={query.data} onOpenDay={openDay} />
          )}
          <MonthClose summary={query.data} />
        </>
      )}
    </div>
  )
}
