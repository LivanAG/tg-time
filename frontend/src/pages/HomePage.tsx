import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import { summaryApi } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { DashboardDto, DashboardMonthDto, DashboardVacationsDto } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Duration } from '../components/Duration'
import { IssueList } from '../components/IssueList'
import { buttonClasses } from '../components/buttonClasses'
import { Alert, Button, Card, QueryError, Spinner, Stat } from '../components/ui'
import { useDayEditor } from '../features/workday/DayEditorContext'
import { findPeriodForDate, useSelectedPeriod } from '../hooks/usePeriods'
import { capitalize, formatDate, formatDateLong, formatMonthLong, monthOf } from '../lib/dates'
import { formatDays, formatNumber, formatPct } from '../lib/format'
import { formatMinutes } from '../lib/time'
import { timesSummary } from './record/dayInfo'

export function HomePage() {
  const { user } = useAuth()
  const query = useQuery({ queryKey: queryKeys.dashboard, queryFn: summaryApi.dashboard })

  if (query.isPending) {
    return <Spinner label="Cargando tu resumen…" />
  }
  if (query.isError) {
    return <QueryError error={query.error} onRetry={() => void query.refetch()} />
  }
  const dashboard = query.data
  const firstName = user?.name.split(' ')[0] ?? ''

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Hola{firstName ? `, ${firstName}` : ''}</h1>
        <p className="text-sm text-slate-600">{capitalize(formatDateLong(dashboard.today))}</p>
      </header>
      {dashboard.period ? <Dashboard dashboard={dashboard} /> : <WelcomeWizard />}
    </div>
  )
}

/** Dónde queda hoy respecto al periodo seleccionado. */
type PeriodTiming = 'current' | 'future' | 'past'

const BALANCE_LABELS: Record<PeriodTiming, string> = {
  current: 'Saldo acumulado hasta hoy',
  future: 'Saldo al empezar el periodo',
  past: 'Saldo final del periodo',
}

function periodTiming({ today, period }: DashboardDto): PeriodTiming {
  if (period && today < period.startDate) {
    return 'future'
  }
  return period && today > period.endDate ? 'past' : 'current'
}

function Dashboard({ dashboard }: { dashboard: DashboardDto }) {
  const { openDay } = useDayEditor()
  const { periods, selectPeriod } = useSelectedPeriod()
  const today = dashboard.todayWorkday
  const period = dashboard.period
  const timing = periodTiming(dashboard)
  // Si hoy cae en otro periodo, se ofrece cambiar a él para fichar.
  const periodWithToday = timing === 'current' ? undefined : findPeriodForDate(periods, dashboard.today)

  return (
    <>
      {period && timing !== 'current' && (
        <Alert title={`Estás trabajando con el periodo ${period.name}`}>
          {formatDate(period.startDate)} – {formatDate(period.endDate)}:{' '}
          {timing === 'future'
            ? 'aún no ha empezado. Se muestra su primer mes.'
            : 'ya ha terminado. Se muestran su último mes y su saldo final.'}
        </Alert>
      )}
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="flex flex-col justify-between">
          <p className="text-sm font-medium text-slate-600">{BALANCE_LABELS[timing]}</p>
          <p className="mt-1 text-5xl font-bold tracking-tight">
            <Duration minutes={dashboard.balanceToDateMinutes} />
          </p>
          <p className="mt-2 text-xs text-slate-500">
            Periodo {dashboard.period?.name}. Positivo: te sobran horas; negativo: te faltan.
          </p>
        </Card>

        <Card className="flex flex-col gap-3">
          <p className="text-sm font-medium text-slate-600">Hoy</p>
          {timing !== 'current' ? (
            <>
              <p className="text-slate-800">Hoy no está dentro del periodo {period?.name}.</p>
              {periodWithToday && (
                <Button
                  variant="secondary"
                  className="w-full sm:w-auto"
                  onClick={() => selectPeriod(periodWithToday.id)}
                >
                  Cambiar a {periodWithToday.name} para fichar hoy
                </Button>
              )}
            </>
          ) : today ? (
            <p className="text-slate-800">
              <span className="text-lg font-semibold tabular-nums">
                {timesSummary(today)}
              </span>{' '}
              · total <strong className="tabular-nums">{formatMinutes(today.totals.workedMinutes)}</strong>
            </p>
          ) : dashboard.todayIsWorkingDay ? (
            <p className="text-slate-800">
              Día laborable (jornada de {formatMinutes(dashboard.todayDayMinutes)}). Aún no has fichado.
            </p>
          ) : (
            <p className="text-slate-800">Hoy no es laborable.</p>
          )}
          {timing === 'current' && (
            <Button size="lg" className="w-full sm:w-auto" onClick={() => openDay(dashboard.today)}>
              {today ? 'Editar el fichaje de hoy' : 'Fichar hoy'}
            </Button>
          )}
        </Card>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {dashboard.currentMonth && (
          <CurrentMonthCard
            month={dashboard.currentMonth}
            isCurrent={dashboard.currentMonth.month === monthOf(dashboard.today)}
          />
        )}
        {dashboard.vacations && <VacationsCard vacations={dashboard.vacations} />}
        {dashboard.currentMonth && <RemoteCard month={dashboard.currentMonth} />}
        <Card title="Horas a recuperar y proyección" titleId="home-projection">
          <dl className="grid grid-cols-2 gap-4">
            <Stat label="A recuperar" hint="Vacaciones que no cubre el margen">
              <Duration minutes={dashboard.hoursToRecoverMinutes} signed={false} />
            </Stat>
            <Stat label="Fin de periodo" hint="Positivo sobra, negativo falta">
              <Duration minutes={dashboard.projectionMinutes} />
            </Stat>
          </dl>
          <Link to="/resumen" className="mt-3 inline-block text-sm font-medium text-sky-800 underline">
            Ver el resumen anual
          </Link>
        </Card>
        {dashboard.currentMonth && <WarningsCard month={dashboard.currentMonth} />}
      </div>
    </>
  )
}

function CurrentMonthCard({ month, isCurrent }: { month: DashboardMonthDto; isCurrent: boolean }) {
  const title = isCurrent ? `Mes actual: ${formatMonthLong(month.month)}` : capitalize(formatMonthLong(month.month))
  return (
    <Card title={title} titleId="home-month">
      <dl className="grid grid-cols-3 gap-3">
        <Stat label="Teóricas">
          <Duration minutes={month.theoreticalMinutes} signed={false} />
        </Stat>
        <Stat label="Hechas">
          <Duration minutes={month.workedMinutes} signed={false} />
        </Stat>
        <Stat label="Diferencia">
          <Duration minutes={month.differenceMinutes} />
        </Stat>
      </dl>
      <p className="mt-4 text-xs font-semibold uppercase tracking-wide text-slate-500">Hasta hoy</p>
      <dl className="mt-1 grid grid-cols-3 gap-3">
        <Stat label="Teóricas">
          <Duration minutes={month.theoreticalToDateMinutes} signed={false} />
        </Stat>
        <Stat label="Hechas">
          <Duration minutes={month.workedToDateMinutes} signed={false} />
        </Stat>
        <Stat label="Diferencia">
          <Duration minutes={month.differenceToDateMinutes} />
        </Stat>
      </dl>
      <Link to={`/registro/${month.month}`} className="mt-3 inline-block text-sm font-medium text-sky-800 underline">
        Abrir el registro del mes
      </Link>
    </Card>
  )
}

function VacationsCard({ vacations }: { vacations: DashboardVacationsDto }) {
  return (
    <Card title="Vacaciones" titleId="home-vacations">
      <p className="text-3xl font-bold text-slate-900">
        {formatNumber(vacations.remainingDays)}
        <span className="ml-1 text-base font-medium text-slate-600">de {vacations.totalDays} días restantes</span>
      </p>
      <dl className="mt-3 grid grid-cols-2 gap-3">
        <Stat label="Disfrutadas">{formatDays(vacations.takenDays)}</Stat>
        <Stat label="Planificadas">{formatDays(vacations.pendingPlannedDays)}</Stat>
      </dl>
      <Link to="/calendario" className="mt-3 inline-block text-sm font-medium text-sky-800 underline">
        Planificar en el calendario
      </Link>
    </Card>
  )
}

function Meter({ value, max, label }: { value: number; max: number; label: string }) {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  const exceeded = value > max
  return (
    <div>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={value}
        className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
      >
        <div className={`h-full rounded-full ${exceeded ? 'bg-red-500' : 'bg-sky-600'}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}

function RemoteCard({ month }: { month: DashboardMonthDto }) {
  const pctExceeded = month.remotePct > month.maxRemotePct
  return (
    <Card title="Teletrabajo del mes" titleId="home-remote">
      <div className="space-y-4">
        <div>
          <p className="flex items-baseline justify-between text-sm">
            <span className="text-slate-600">Porcentaje en casa</span>
            <span className={`font-semibold ${pctExceeded ? 'text-red-600' : 'text-slate-900'}`}>
              {formatPct(month.remotePct)}{' '}
              <span className="font-normal text-slate-500">/ máx. {formatPct(month.maxRemotePct)}</span>
            </span>
          </p>
          <Meter value={month.remotePct} max={month.maxRemotePct} label="Porcentaje de teletrabajo frente al máximo" />
        </div>
        <p className="flex items-baseline justify-between text-sm">
          <span className="text-slate-600">Días en casa o mixtos</span>
          <span className="font-semibold text-slate-900">{month.remoteDays}</span>
        </p>
      </div>
    </Card>
  )
}

function WarningsCard({ month }: { month: DashboardMonthDto }) {
  const clean = month.warnings.length === 0
  return (
    <Card title="Avisos del mes" titleId="home-warnings">
      {clean ? (
        <p className="text-sm text-emerald-700">Sin avisos este mes.</p>
      ) : (
        <div className="space-y-2">
          <IssueList issues={month.warnings} />
          <Link to={`/registro/${month.month}`} className="inline-block text-sm font-medium text-sky-800 underline">
            Revisar en el registro
          </Link>
        </div>
      )}
    </Card>
  )
}

/** Sin periodo que incluya hoy: pasos para empezar. */
function WelcomeWizard() {
  return (
    <Card title="Primeros pasos" titleId="welcome">
      <p className="text-sm text-slate-600">
        Aún no tienes un periodo que incluya hoy. Con dos pasos tendrás tu Excel dentro de la app:
      </p>
      <ol className="mt-4 space-y-4">
        <li className="flex gap-3">
          <span
            aria-hidden="true"
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-sky-700 text-sm font-semibold text-white"
          >
            1
          </span>
          <div>
            <p className="font-medium text-slate-900">Crea el periodo anual</p>
            <p className="text-sm text-slate-600">
              El formulario ya trae los valores de tu Excel: del 26/05/2026 al 25/05/2027, 1760 h de convenio, 23 días
              de vacaciones, jornada de 8:00 e intensiva de 7:00 del 15/06 al 15/09 y los festivos de Madrid.
            </p>
            <Link to="/ajustes?seccion=periodos&nuevo=1" className={buttonClasses('primary', 'sm', 'mt-2')}>
              Crear el periodo
            </Link>
          </div>
        </li>
        <li className="flex gap-3">
          <span
            aria-hidden="true"
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-sky-700 text-sm font-semibold text-white"
          >
            2
          </span>
          <div>
            <p className="font-medium text-slate-900">Importa tu Excel</p>
            <p className="text-sm text-slate-600">
              Sube HORAS_IZERTIS_2026-27.xlsx: verás una vista previa con los días que se importan, las vacaciones
              deducidas y los días futuros que se descartan antes de confirmar.
            </p>
            <Link to="/ajustes?seccion=importar" className={buttonClasses('secondary', 'sm', 'mt-2')}>
              Importar el Excel
            </Link>
          </div>
        </li>
        <li className="flex gap-3">
          <span
            aria-hidden="true"
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-300 text-sm font-semibold text-slate-800"
          >
            3
          </span>
          <div>
            <p className="font-medium text-slate-900">Ficha cada día</p>
            <p className="text-sm text-slate-600">Desde Inicio con “Fichar hoy” o desde el Registro mensual.</p>
          </div>
        </li>
      </ol>
    </Card>
  )
}
