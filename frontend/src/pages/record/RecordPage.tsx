import { useMutation, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'

import { errorMessage, isApiError, saveBlob } from '../../api/client'
import { importExportApi, summaryApi } from '../../api/endpoints'
import { queryKeys } from '../../api/queryKeys'
import { buttonClasses } from '../../components/buttonClasses'
import { Alert, Button, QueryError, Spinner } from '../../components/ui'
import { useDayEditor } from '../../features/workday/DayEditorContext'
import { useIsDesktop } from '../../hooks/useMediaQuery'
import { usePeriods } from '../../hooks/usePeriods'
import { capitalize, formatMonthLong, isYearMonth, monthOf, shiftMonth, splitMonth, todayIso } from '../../lib/dates'
import type { CalcRules } from '../../lib/workdayCalc'
import { MonthCards } from './MonthCards'
import { MonthClose } from './MonthClose'
import { MonthTable } from './MonthTable'

/** /registro/:month — hoja mensual. Sin mes válido, redirige al mes actual. */
export function RecordPage() {
  const { month } = useParams()
  if (!isYearMonth(month)) {
    return <Navigate to={`/registro/${monthOf(todayIso())}`} replace />
  }
  return <MonthRecord key={month} month={month} />
}

function MonthRecord({ month }: { month: string }) {
  const { year, month: monthNumber } = splitMonth(month)
  const { openDay } = useDayEditor()
  const isDesktop = useIsDesktop()
  const [notice, setNotice] = useState<string | null>(null)
  const query = useQuery({ queryKey: queryKeys.month(month), queryFn: () => summaryApi.month(year, monthNumber) })
  const periodsQuery = usePeriods()
  const exportMonth = useMutation({
    mutationFn: () => importExportApi.exportXlsx(year, monthNumber),
    onSuccess: saveBlob,
  })

  const period = periodsQuery.data?.find((p) => p.id === query.data?.periodId)
  const breakfastToleranceMin = period?.breakfastToleranceMin ?? 20
  const minLunchMin = period?.minLunchMin ?? 30
  const rules = useMemo<CalcRules>(() => ({ breakfastToleranceMin, minLunchMin }), [breakfastToleranceMin, minLunchMin])
  const title = capitalize(formatMonthLong(month))
  const current = monthOf(todayIso())

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Registro</h1>
          <p className="text-sm text-slate-600">{title}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <nav aria-label="Cambiar de mes" className="flex items-center gap-1">
            <Link
              to={`/registro/${shiftMonth(month, -1)}`}
              className={buttonClasses('secondary', 'sm')}
              aria-label="Mes anterior"
            >
              ‹ <span className="hidden sm:inline">{capitalize(formatMonthLong(shiftMonth(month, -1)))}</span>
            </Link>
            {month !== current && (
              <Link to={`/registro/${current}`} className={buttonClasses('ghost', 'sm')}>
                Mes actual
              </Link>
            )}
            <Link
              to={`/registro/${shiftMonth(month, 1)}`}
              className={buttonClasses('secondary', 'sm')}
              aria-label="Mes siguiente"
            >
              <span className="hidden sm:inline">{capitalize(formatMonthLong(shiftMonth(month, 1)))}</span> ›
            </Link>
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
