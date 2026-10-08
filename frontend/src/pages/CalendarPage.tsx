import { useQuery } from '@tanstack/react-query'
import { getDaysInMonth } from 'date-fns'
import { useMemo, useState } from 'react'

import { periodsApi, summaryApi } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import type { CalendarDayDto, PeriodDto, PeriodSummaryDto, YearMonth } from '../api/types'
import { IssueList } from '../components/IssueList'
import { NoPeriod } from '../components/NoPeriod'
import { Card, QueryError, Spinner, Stat } from '../components/ui'
import { AbsenceDialog } from '../features/absence/AbsenceDialog'
import { useDayEditor } from '../features/workday/DayEditorContext'
import { useSelectedPeriod } from '../hooks/usePeriods'
import { capitalize, formatDateLong, formatMonthLong, isoWeekday, splitMonth, todayIso } from '../lib/dates'
import { absenceLabel, formatDays } from '../lib/format'
import { formatMinutes } from '../lib/time'

const WEEKDAYS = ['L', 'M', 'X', 'J', 'V', 'S', 'D']

/** Clases por tipo de día (cadenas completas para que Tailwind las genere). */
const TONES = {
  normal: 'bg-white text-slate-800 border border-slate-300',
  intensive: 'bg-amber-100 text-amber-950 border border-amber-300',
  holiday: 'bg-rose-200 text-rose-950',
  weekend: 'bg-slate-100 text-slate-400',
  outside: 'text-slate-300',
  VACACIONES: 'bg-emerald-500 text-white',
  PUENTE: 'bg-violet-500 text-white',
  PERMISO: 'bg-cyan-600 text-white',
  BAJA: 'bg-orange-500 text-white',
  half_VACACIONES: 'bg-linear-to-br from-emerald-500 from-50% to-white to-50% text-slate-900 border border-emerald-500',
  half_PUENTE: 'bg-linear-to-br from-violet-500 from-50% to-white to-50% text-slate-900 border border-violet-500',
  half_PERMISO: 'bg-linear-to-br from-cyan-600 from-50% to-white to-50% text-slate-900 border border-cyan-600',
  half_BAJA: 'bg-linear-to-br from-orange-500 from-50% to-white to-50% text-slate-900 border border-orange-500',
} as const

function dayTone(day: CalendarDayDto): string {
  if (day.dayType === 'FESTIVO') {
    return TONES.holiday
  }
  if (day.dayType === 'FIN_DE_SEMANA') {
    return TONES.weekend
  }
  if (day.absence) {
    return day.absence.halfDay ? TONES[`half_${day.absence.type}`] : TONES[day.absence.type]
  }
  return day.intensive ? TONES.intensive : TONES.normal
}

function dayDescription(day: CalendarDayDto): string {
  const parts = [capitalize(formatDateLong(day.date))]
  if (day.dayType === 'FESTIVO') {
    parts.push(day.holidayName ? `Festivo: ${day.holidayName}` : 'Festivo')
  } else if (day.dayType === 'FIN_DE_SEMANA') {
    parts.push('Fin de semana')
  } else {
    parts.push(`${day.intensive ? 'Intensiva' : 'Laborable'} de ${formatMinutes(day.dayMinutes)}`)
    if (day.absence) {
      parts.push(absenceLabel(day.absence.type, day.absence.halfDay))
    }
  }
  if (day.hasWorkday) {
    parts.push('con fichaje')
  }
  return parts.join(' · ')
}

function groupByMonth(days: CalendarDayDto[]): [YearMonth, Map<string, CalendarDayDto>][] {
  const months = new Map<YearMonth, Map<string, CalendarDayDto>>()
  for (const day of days) {
    const key = day.date.slice(0, 7)
    if (!months.has(key)) {
      months.set(key, new Map())
    }
    months.get(key)!.set(day.date, day)
  }
  return [...months.entries()]
}

export function CalendarPage() {
  const { periodsQuery, period } = useSelectedPeriod()

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Calendario</h1>
          <p className="text-sm text-slate-600">Toca un día laborable para marcar o quitar una ausencia.</p>
        </div>
      </header>
      {periodsQuery.isPending ? (
        <Spinner />
      ) : periodsQuery.isError ? (
        <QueryError error={periodsQuery.error} onRetry={() => void periodsQuery.refetch()} />
      ) : !period ? (
        <NoPeriod />
      ) : (
        <PeriodCalendar key={period.id} period={period} />
      )}
    </div>
  )
}

function PeriodCalendar({ period }: { period: PeriodDto }) {
  const { openDay } = useDayEditor()
  const [selected, setSelected] = useState<CalendarDayDto | null>(null)
  const calendarQuery = useQuery({
    queryKey: queryKeys.calendar(period.id),
    queryFn: () => periodsApi.calendar(period.id),
  })
  const summaryQuery = useQuery({
    queryKey: queryKeys.periodSummary(period.id),
    queryFn: () => summaryApi.period(period.id),
  })
  const months = useMemo(() => groupByMonth(calendarQuery.data ?? []), [calendarQuery.data])
  const today = todayIso()

  return (
    <div className="space-y-4">
      {summaryQuery.data && <VacationCounter summary={summaryQuery.data} />}
      <Legend />
      {calendarQuery.isPending ? (
        <Spinner label="Cargando el calendario…" />
      ) : calendarQuery.isError ? (
        <QueryError error={calendarQuery.error} onRetry={() => void calendarQuery.refetch()} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
          {months.map(([month, days]) => (
            <MonthGrid key={month} month={month} days={days} today={today} onSelect={setSelected} />
          ))}
        </div>
      )}
      {selected && (
        <AbsenceDialog
          key={selected.date}
          date={selected.date}
          periodId={period.id}
          absence={selected.absence}
          dayMinutes={selected.dayMinutes}
          onClose={() => setSelected(null)}
          onOpenWorkday={() => {
            const date = selected.date
            setSelected(null)
            openDay(date)
          }}
        />
      )}
    </div>
  )
}

function VacationCounter({ summary }: { summary: PeriodSummaryDto }) {
  const v = summary.vacations
  return (
    <Card title={`Vacaciones ${summary.name}`} titleId="calendar-vacations">
      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Stat label="Disfrutadas" hint={formatMinutes(v.takenMinutes)}>
          {formatDays(v.takenDays)}
        </Stat>
        <Stat label="Planificadas" hint="Marcadas a partir de mañana">
          {formatDays(v.pendingPlannedDays)}
        </Stat>
        <Stat label="Restantes" hint={`de ${v.totalDays} días`}>
          {formatDays(v.remainingDays)}
        </Stat>
        <Stat label="Sin planificar">{formatDays(v.unplannedDays)}</Stat>
      </dl>
      <IssueList issues={summary.warnings.filter((w) => w.code === 'VACATION_OVERPLANNED')} className="mt-3" />
    </Card>
  )
}

const LEGEND: { label: string; className: string }[] = [
  { label: 'Laborable 8 h', className: TONES.normal },
  { label: 'Intensiva 7 h', className: TONES.intensive },
  { label: 'Festivo', className: TONES.holiday },
  { label: 'Fin de semana', className: TONES.weekend },
  { label: 'Vacaciones', className: TONES.VACACIONES },
  { label: 'Puente', className: TONES.PUENTE },
  { label: 'Permiso', className: TONES.PERMISO },
  { label: 'Baja', className: TONES.BAJA },
  { label: 'Medio día', className: TONES.half_VACACIONES },
]

function Legend() {
  return (
    <section aria-label="Leyenda" className="flex flex-wrap gap-x-4 gap-y-2 text-xs text-slate-700">
      {LEGEND.map((item) => (
        <span key={item.label} className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className={`inline-block h-4 w-4 rounded ${item.className}`} />
          {item.label}
        </span>
      ))}
      <span className="inline-flex items-center gap-1.5">
        <span aria-hidden="true" className="inline-block h-1.5 w-1.5 rounded-full bg-sky-700" />
        Con fichaje
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span aria-hidden="true" className="inline-block h-4 w-4 rounded ring-2 ring-sky-700" />
        Hoy
      </span>
    </section>
  )
}

interface MonthGridProps {
  month: YearMonth
  days: Map<string, CalendarDayDto>
  today: string
  onSelect: (day: CalendarDayDto) => void
}

function MonthGrid({ month, days, today, onSelect }: MonthGridProps) {
  const { year, month: monthNumber } = splitMonth(month)
  const total = getDaysInMonth(new Date(year, monthNumber - 1, 1))
  const first = `${month}-01`
  const offset = isoWeekday(first) - 1
  const dates = Array.from({ length: total }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`)
  const headingId = `cal-${month}`

  return (
    <section aria-labelledby={headingId} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
      <h2 id={headingId} className="mb-2 text-sm font-semibold text-slate-800">
        {capitalize(formatMonthLong(month))}
      </h2>
      <div className="grid grid-cols-7 gap-1 text-center text-xs">
        {WEEKDAYS.map((d) => (
          <span key={d} aria-hidden="true" className="py-0.5 font-medium text-slate-500">
            {d}
          </span>
        ))}
        {Array.from({ length: offset }, (_, i) => (
          <span key={`empty-${i}`} aria-hidden="true" />
        ))}
        {dates.map((date) => {
          const day = days.get(date)
          const number = Number(date.slice(8))
          if (!day) {
            return (
              <span
                key={date}
                className={`flex aspect-square items-center justify-center rounded ${TONES.outside}`}
                title="Fuera del periodo"
              >
                {number}
              </span>
            )
          }
          const description = dayDescription(day)
          const classes = `relative flex aspect-square items-center justify-center rounded text-xs font-medium ${dayTone(day)} ${
            date === today ? 'ring-2 ring-sky-700 ring-offset-1' : ''
          }`
          const dot = day.hasWorkday && (
            <span
              aria-hidden="true"
              className="absolute bottom-0.5 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full bg-sky-700"
            />
          )
          if (day.dayType !== 'LABORABLE') {
            return (
              <span key={date} className={classes} title={description} aria-label={description} role="img">
                {number}
                {dot}
              </span>
            )
          }
          return (
            <button
              key={date}
              type="button"
              className={`${classes} hover:ring-2 hover:ring-sky-400 focus-visible:outline-2 focus-visible:outline-sky-700`}
              title={description}
              aria-label={description}
              onClick={() => onSelect(day)}
            >
              {number}
              {dot}
            </button>
          )
        })}
      </div>
    </section>
  )
}
