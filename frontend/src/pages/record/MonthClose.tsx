import type { MonthSummaryDto } from '../../api/types'
import { Duration } from '../../components/Duration'
import { IssueList } from '../../components/IssueList'
import { Card, Stat } from '../../components/ui'
import { formatDays, formatPct } from '../../lib/format'
import { formatOptionalMinutes } from '../../lib/time'

function differenceLabel(minutes: number): string {
  if (minutes > 0) {
    return 'Sobran'
  }
  return minutes < 0 ? 'Faltan' : 'Diferencia'
}

/** Cierre del mes (pie de la hoja mensual del Excel), con saldos con signo. */
export function MonthClose({ summary }: { summary: MonthSummaryDto }) {
  const remotePctExceeded = summary.remotePct > summary.maxRemotePct
  const remoteDaysExceeded = summary.remoteDays > summary.maxRemoteDaysMonth

  return (
    <Card title="Cierre del mes" titleId="month-close">
      <div className="space-y-5">
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Stat
            label="Teóricas"
            hint={`${summary.workingDays} laborables (${summary.normalDays} a jornada normal, ${summary.intensiveDays} a intensiva)`}
          >
            <Duration minutes={summary.theoreticalMinutes} signed={false} />
          </Stat>
          <Stat label="Hechas" hint={`Redondeado: ${formatOptionalMinutes(summary.roundedMinutes)}`}>
            <Duration minutes={summary.workedMinutes} signed={false} />
          </Stat>
          <Stat label={differenceLabel(summary.differenceMinutes)} hint="Hechas − teóricas">
            <Duration minutes={summary.differenceMinutes} />
          </Stat>
          <Stat label="Horas del mes" hint="Jornadas de todos los laborables">
            <Duration minutes={summary.calendarMinutes} signed={false} />
          </Stat>
        </dl>

        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Hasta hoy</h3>
          <dl className="grid grid-cols-3 gap-4">
            <Stat label="Teóricas">
              <Duration minutes={summary.theoreticalToDateMinutes} signed={false} />
            </Stat>
            <Stat label="Hechas">
              <Duration minutes={summary.workedToDateMinutes} signed={false} />
            </Stat>
            <Stat label={differenceLabel(summary.differenceToDateMinutes)}>
              <Duration minutes={summary.differenceToDateMinutes} />
            </Stat>
          </dl>
        </div>

        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Stat label="Saldo de apertura">
            <Duration minutes={summary.openingBalanceMinutes} />
          </Stat>
          <Stat label="Saldo de cierre" hint="Apertura + diferencia − puentes">
            <Duration minutes={summary.closingBalanceMinutes} />
          </Stat>
          <Stat label="Vacaciones" hint={formatOptionalMinutes(summary.vacationMinutes)}>
            {formatDays(summary.vacationDays)}
          </Stat>
          <Stat label="Puentes a recuperar" hint={formatOptionalMinutes(summary.bridgeMinutes)}>
            {formatDays(summary.bridgeDays)}
          </Stat>
        </dl>

        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Stat label="Teletrabajo" hint={`Máximo ${formatPct(summary.maxRemotePct)}`}>
            <span className={remotePctExceeded ? 'text-red-600' : ''}>{formatPct(summary.remotePct)}</span>
          </Stat>
          <Stat label="Días en casa" hint={`Máximo ${summary.maxRemoteDaysMonth} al mes`}>
            <span className={remoteDaysExceeded ? 'text-red-600' : ''}>{summary.remoteDays}</span>
          </Stat>
          <Stat label="En casa">
            <Duration minutes={summary.remoteMinutes} signed={false} />
          </Stat>
          <Stat label="En oficina">
            <Duration minutes={summary.officeMinutes} signed={false} />
          </Stat>
        </dl>

        <dl className="grid grid-cols-3 gap-4">
          <Stat label="JIRA">{formatOptionalMinutes(summary.jiraMinutes)}</Stat>
          <Stat label="IZERTIA">{formatOptionalMinutes(summary.izertiaMinutes)}</Stat>
          <Stat label="Días con avisos de imputación">
            <span className={summary.imputationWarningDays > 0 ? 'text-amber-700' : ''}>
              {summary.imputationWarningDays}
            </span>
          </Stat>
        </dl>

        <IssueList issues={summary.warnings} />
      </div>
    </Card>
  )
}
