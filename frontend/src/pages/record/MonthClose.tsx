import type { ReactNode } from 'react'

import type { MonthSummaryDto } from '../../api/types'
import { Duration } from '../../components/Duration'
import { IssueList } from '../../components/IssueList'
import { Card } from '../../components/ui'
import { formatDays, formatPct } from '../../lib/format'
import { formatMinutes } from '../../lib/time'

function SectionTitle({ children }: { children: ReactNode }) {
  return <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{children}</h3>
}

/** Fila "concepto ........ valor" de una lista. */
function Row({ label, children, strong = false }: { label: ReactNode; children: ReactNode; strong?: boolean }) {
  return (
    <div className={`flex items-baseline justify-between gap-4 py-1.5 ${strong ? 'font-semibold' : ''}`}>
      <dt className={strong ? 'text-slate-900' : 'text-slate-600'}>{label}</dt>
      <dd className="text-right tabular-nums text-slate-900">{children}</dd>
    </div>
  )
}

/**
 * Cierre del mes (pie de la hoja mensual del Excel): horas del mes y hasta hoy en una tabla, el saldo
 * como una cuenta (apertura + diferencia − puentes = cierre) y ausencias y teletrabajo en dos listas.
 */
export function MonthClose({ summary }: { summary: MonthSummaryDto }) {
  const remotePctExceeded = summary.remotePct > summary.maxRemotePct

  return (
    <Card title="Cierre del mes" titleId="month-close">
      <div className="space-y-6 text-sm">
        <div className="grid gap-6 lg:grid-cols-2">
          <div>
            <SectionTitle>Horas</SectionTitle>
            <table className="w-full">
              <caption className="sr-only">
                Horas teóricas, hechas sin redondear y redondeadas, y diferencia del mes y hasta hoy
              </caption>
              <thead>
                <tr className="border-b border-slate-200 text-xs text-slate-500">
                  <th scope="col" className="py-1.5 text-left font-medium">
                    <span className="sr-only">Concepto</span>
                  </th>
                  <th scope="col" className="py-1.5 text-right font-medium">
                    Mes completo
                  </th>
                  <th scope="col" className="py-1.5 text-right font-medium">
                    Hasta hoy
                  </th>
                </tr>
              </thead>
              <tbody className="tabular-nums">
                <tr className="border-b border-slate-100">
                  <th scope="row" className="py-1.5 text-left font-normal text-slate-600">
                    Teóricas
                  </th>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.theoreticalMinutes} signed={false} />
                  </td>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.theoreticalToDateMinutes} signed={false} />
                  </td>
                </tr>
                <tr className="border-b border-slate-100">
                  <th scope="row" className="py-1.5 text-left font-normal text-slate-600">
                    Hechas sin redondear
                  </th>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.workedMinutes} signed={false} />
                  </td>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.workedToDateMinutes} signed={false} />
                  </td>
                </tr>
                <tr className="border-b border-slate-100">
                  <th scope="row" className="py-1.5 text-left font-normal text-slate-600">
                    Hechas redondeadas
                  </th>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.roundedMinutes} signed={false} />
                  </td>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.roundedToDateMinutes} signed={false} />
                  </td>
                </tr>
                <tr className="font-semibold">
                  <th scope="row" className="py-1.5 text-left text-slate-900">
                    Diferencia
                  </th>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.differenceMinutes} />
                  </td>
                  <td className="py-1.5 text-right">
                    <Duration minutes={summary.differenceToDateMinutes} />
                  </td>
                </tr>
              </tbody>
            </table>
            <p className="mt-2 text-xs text-slate-500">
              {summary.workingDays} laborables ({summary.normalDays} a jornada normal, {summary.intensiveDays} a
              intensiva): {formatMinutes(summary.calendarMinutes)} de jornada. La diferencia y el saldo se calculan con
              las horas sin redondear; el redondeado (tramos de 15 min) es lo que se imputa.
            </p>
          </div>

          <div>
            <SectionTitle>Saldo</SectionTitle>
            <dl className="divide-y divide-slate-100">
              <Row label="Saldo de apertura">
                <Duration minutes={summary.openingBalanceMinutes} />
              </Row>
              <Row label="+ Diferencia del mes">
                <Duration minutes={summary.differenceMinutes} />
              </Row>
              <Row label={`− Puentes a recuperar (${formatDays(summary.bridgeDays)})`}>
                <Duration minutes={summary.bridgeMinutes} signed={false} />
              </Row>
            </dl>
            <dl className="mt-1 border-t-2 border-slate-300">
              <Row label="= Saldo de cierre" strong>
                <Duration minutes={summary.closingBalanceMinutes} />
              </Row>
            </dl>
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <div>
            <SectionTitle>Ausencias</SectionTitle>
            <dl className="divide-y divide-slate-100">
              <Row label="Vacaciones">
                {formatDays(summary.vacationDays)} · {formatMinutes(summary.vacationMinutes)}
              </Row>
              <Row label="Puentes a recuperar">
                {formatDays(summary.bridgeDays)} · {formatMinutes(summary.bridgeMinutes)}
              </Row>
            </dl>
          </div>

          <div>
            <SectionTitle>Teletrabajo</SectionTitle>
            <dl className="divide-y divide-slate-100">
              <Row label="En casa">
                <Duration minutes={summary.remoteMinutes} signed={false} />
                <span className="ml-1 text-slate-500">
                  ({summary.remoteDays} {summary.remoteDays === 1 ? 'día' : 'días'} en casa o mixto)
                </span>
              </Row>
              <Row label="En oficina">
                <Duration minutes={summary.officeMinutes} signed={false} />
              </Row>
              <Row label="Porcentaje en casa">
                <span className={remotePctExceeded ? 'font-semibold text-red-600' : ''}>
                  {formatPct(summary.remotePct)}
                </span>
                <span className="ml-1 text-slate-500">/ máx. {formatPct(summary.maxRemotePct)}</span>
              </Row>
            </dl>
          </div>
        </div>

        <IssueList issues={summary.warnings} />
      </div>
    </Card>
  )
}
