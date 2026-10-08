import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import { summaryApi } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { MonthRowDto, PeriodDto, PeriodSummaryDto } from '../api/types'
import { Duration } from '../components/Duration'
import { IssueList } from '../components/IssueList'
import { NoPeriod } from '../components/NoPeriod'
import { Card, QueryError, Spinner, Stat } from '../components/ui'
import { useSelectedPeriod } from '../hooks/usePeriods'
import { capitalize, formatDate, formatMonthShort } from '../lib/dates'
import { formatDays, formatNumber } from '../lib/format'
import { formatMinutes } from '../lib/time'

/** /resumen — equivalente a la hoja Horas del Excel. */
export function SummaryPage() {
  const { periodsQuery, period } = useSelectedPeriod()

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Resumen anual</h1>
          <p className="text-sm text-slate-600">Horas del periodo frente al convenio (hoja Horas del Excel).</p>
        </div>
      </header>
      {periodsQuery.isPending ? (
        <Spinner />
      ) : periodsQuery.isError ? (
        <QueryError error={periodsQuery.error} onRetry={() => void periodsQuery.refetch()} />
      ) : !period ? (
        <NoPeriod />
      ) : (
        <PeriodSummary key={period.id} period={period} />
      )}
    </div>
  )
}

function PeriodSummary({ period }: { period: PeriodDto }) {
  const query = useQuery({ queryKey: queryKeys.periodSummary(period.id), queryFn: () => summaryApi.period(period.id) })
  if (query.isPending) {
    return <Spinner label="Calculando el resumen…" />
  }
  if (query.isError) {
    return <QueryError error={query.error} onRetry={() => void query.refetch()} />
  }
  const summary = query.data
  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        {summary.name}: del {formatDate(summary.startDate)} al {formatDate(summary.endDate)} · datos a{' '}
        {formatDate(summary.today)}
      </p>
      <MonthsTable summary={summary} />
      <Balance summary={summary} />
    </div>
  )
}

function sum(rows: MonthRowDto[], pick: (row: MonthRowDto) => number): number {
  return rows.reduce((total, row) => total + pick(row), 0)
}

function MonthsTable({ summary }: { summary: PeriodSummaryDto }) {
  const rows = summary.months
  const hasBridges = rows.some((r) => r.bridgeMinutes !== 0)
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
      <table className="w-full min-w-[880px] border-collapse text-sm">
        <caption className="sr-only">Horas por mes del periodo</caption>
        <thead className="bg-slate-100 text-xs font-semibold uppercase tracking-wide text-slate-600">
          <tr>
            <th scope="col" className="sticky left-0 bg-slate-100 px-3 py-2 text-left">
              Mes
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Días lab.
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              A 8 h
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              A 7 h
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Horas mes
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Vacaciones
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Teóricas
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Hechas
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Diferencia
            </th>
            {hasBridges && (
              <th scope="col" className="px-2 py-2 text-right">
                Puentes
              </th>
            )}
            <th scope="col" className="px-3 py-2 text-right">
              Saldo acumulado
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const future = row.status === 'FUTURE'
            return (
              <tr
                key={row.month}
                className={`border-t border-slate-100 ${future ? 'text-slate-400' : ''} ${row.status === 'CURRENT' ? 'bg-sky-50' : ''}`}
                data-status={row.status}
              >
                <th
                  scope="row"
                  className={`sticky left-0 px-3 py-2 text-left font-medium ${row.status === 'CURRENT' ? 'bg-sky-50' : 'bg-white'}`}
                >
                  <Link to={`/registro/${row.month}`} className="hover:underline">
                    {capitalize(formatMonthShort(row.month))}
                  </Link>
                  {row.status === 'CURRENT' && <span className="ml-1 text-xs font-normal text-sky-700">(actual)</span>}
                </th>
                <td className="px-2 py-2 text-right tabular-nums">{row.workingDays}</td>
                <td className="px-2 py-2 text-right tabular-nums">{row.normalDays}</td>
                <td className="px-2 py-2 text-right tabular-nums">{row.intensiveDays}</td>
                <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(row.calendarMinutes)}</td>
                <td className="px-2 py-2 text-right tabular-nums">
                  {row.vacationDays > 0
                    ? `${formatNumber(row.vacationDays)} d · ${formatMinutes(row.vacationMinutes)}`
                    : '—'}
                </td>
                <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(row.theoreticalMinutes)}</td>
                <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(row.workedMinutes)}</td>
                <td className="px-2 py-2 text-right">
                  {future ? (
                    formatMinutes(row.differenceMinutes, { signed: true })
                  ) : (
                    <Duration minutes={row.differenceMinutes} />
                  )}
                </td>
                {hasBridges && (
                  <td className="px-2 py-2 text-right tabular-nums">
                    {row.bridgeMinutes ? formatMinutes(row.bridgeMinutes) : '—'}
                  </td>
                )}
                <td className="px-3 py-2 text-right font-medium">
                  {future ? (
                    formatMinutes(row.cumulativeBalanceMinutes, { signed: true })
                  ) : (
                    <Duration minutes={row.cumulativeBalanceMinutes} />
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
        <tfoot className="border-t-2 border-slate-300 bg-slate-50 font-semibold">
          <tr>
            <th scope="row" className="sticky left-0 bg-slate-50 px-3 py-2 text-left">
              Total
            </th>
            <td className="px-2 py-2 text-right tabular-nums">{summary.workingDays}</td>
            <td className="px-2 py-2 text-right tabular-nums">{summary.normalDays}</td>
            <td className="px-2 py-2 text-right tabular-nums">{summary.intensiveDays}</td>
            <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(summary.calendarMinutes)}</td>
            <td className="px-2 py-2 text-right tabular-nums">
              {formatNumber(sum(rows, (r) => r.vacationDays))} d · {formatMinutes(sum(rows, (r) => r.vacationMinutes))}
            </td>
            <td className="px-2 py-2 text-right tabular-nums">
              {formatMinutes(sum(rows, (r) => r.theoreticalMinutes))}
            </td>
            <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(summary.workedMinutes)}</td>
            <td className="px-2 py-2 text-right">
              <Duration minutes={sum(rows, (r) => r.differenceMinutes)} />
            </td>
            {hasBridges && (
              <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(summary.bridgeMinutes)}</td>
            )}
            <td className="px-3 py-2 text-right" />
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

function Balance({ summary }: { summary: PeriodSummaryDto }) {
  const v = summary.vacations
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card title="Margen sobre el convenio" titleId="summary-margin">
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <Stat label="Horas calendario" hint={`${summary.workingDays} laborables`}>
            {formatMinutes(summary.calendarMinutes)}
          </Stat>
          <Stat label="Convenio">{formatMinutes(summary.agreementMinutes)}</Stat>
          <Stat label="Margen" hint="Calendario − convenio">
            <Duration minutes={summary.marginMinutes} />
          </Stat>
          <Stat label="Valor de las vacaciones" hint="Marcadas + sin planificar a jornada normal">
            {formatMinutes(v.valueMinutes)}
          </Stat>
          <Stat label="Horas a recuperar" hint="max(0, vacaciones − margen)">
            <Duration minutes={summary.hoursToRecoverMinutes} signed={false} />
          </Stat>
          <Stat label="Margen restante" hint="Margen − vacaciones disfrutadas">
            <Duration minutes={summary.remainingMarginMinutes} />
          </Stat>
        </dl>
      </Card>

      <Card title="Vacaciones y horas" titleId="summary-hours">
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <Stat label="Vacaciones disfrutadas" hint={formatMinutes(v.takenMinutes)}>
            {formatDays(v.takenDays)}
          </Stat>
          <Stat label="Restantes" hint={`de ${v.totalDays} · ${formatMinutes(v.remainingMinutes)}`}>
            {formatDays(v.remainingDays)}
          </Stat>
          <Stat label="Planificadas" hint={`${formatDays(v.pendingPlannedDays)} pendientes`}>
            {formatDays(v.plannedDays)}
          </Stat>
          <Stat label="Horas hechas">{formatMinutes(summary.workedMinutes)}</Stat>
          <Stat label="Teóricas restantes">{formatMinutes(summary.theoreticalRemainingMinutes)}</Stat>
          <Stat
            label="Saldo hasta hoy"
            hint={`Saldo inicial ${formatMinutes(summary.openingBalanceMinutes, { signed: true })}`}
          >
            <Duration minutes={summary.balanceToDateMinutes} />
          </Stat>
        </dl>
        <div className="mt-4 rounded-lg bg-slate-50 p-3">
          <p className="text-sm text-slate-600">Proyección a fin de periodo</p>
          <p className="text-3xl font-bold">
            <Duration minutes={summary.projectionMinutes} />
          </p>
          <p className="text-xs text-slate-500">
            Hechas + teóricas restantes − vacaciones sin planificar − convenio. Positivo: sobran horas; negativo:
            faltan.
          </p>
        </div>
      </Card>

      {summary.warnings.length > 0 && (
        <Card title="Avisos" titleId="summary-warnings" className="lg:col-span-2">
          <IssueList issues={summary.warnings} />
        </Card>
      )}
    </div>
  )
}
