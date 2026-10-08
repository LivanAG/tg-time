import type { DayDto, MonthSummaryDto } from '../../api/types'
import { Duration } from '../../components/Duration'
import { capitalize, formatDayMonth, formatDayShort, todayIso } from '../../lib/dates'
import { LOCATION_LABELS } from '../../lib/format'
import { formatMinutes } from '../../lib/time'
import { breaksSummary, dayLabel, dayTone, groupByWeek, timesSummary } from './dayInfo'

/** Vista móvil del registro: una tarjeta por día, agrupadas por semana. Tocar un día abre el editor. */
export function MonthCards({ summary, onOpenDay }: { summary: MonthSummaryDto; onOpenDay: (date: string) => void }) {
  const today = todayIso()
  return (
    <div className="space-y-5">
      {groupByWeek(summary).map(({ week, days }) => (
        <section
          key={week.weekStart}
          aria-label={`Semana del ${formatDayMonth(week.weekStart)} al ${formatDayMonth(week.weekEnd)}`}
        >
          <header className="mb-2 flex items-baseline justify-between px-1 text-sm">
            <h3 className="font-semibold text-slate-700">
              {formatDayMonth(week.weekStart)} – {formatDayMonth(week.weekEnd)}
            </h3>
            <span className="text-slate-600">
              <span className="tabular-nums">{formatMinutes(week.workedMinutes)}</span> de{' '}
              <span className="tabular-nums">{formatMinutes(week.theoreticalMinutes)}</span> ·{' '}
              <Duration minutes={week.workedMinutes - week.theoreticalMinutes} />
            </span>
          </header>
          <ul className="space-y-2">
            {days.map((day) => (
              <li key={day.date}>
                <DayCard day={day} isToday={day.date === today} onOpenDay={onOpenDay} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function DayCard({ day, isToday, onOpenDay }: { day: DayDto; isToday: boolean; onOpenDay: (date: string) => void }) {
  const workday = day.workday
  const label = dayLabel(day)
  const outOfPeriod = day.dayType === 'FUERA_DE_PERIODO'
  const compact = !workday && day.dayType !== 'LABORABLE'

  const content = (
    <>
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className={`text-sm font-semibold ${isToday ? 'text-sky-800' : 'text-slate-800'}`}>
            {capitalize(formatDayShort(day.date))}
            {isToday && <span className="ml-2 rounded bg-sky-700 px-1.5 py-0.5 text-xs text-white">Hoy</span>}
          </p>
          {workday ? (
            <p className="text-base tabular-nums text-slate-900">
              {timesSummary(workday)}
            </p>
          ) : (
            <p className="text-sm text-slate-500">{label ?? 'Sin fichaje'}</p>
          )}
        </div>
        {workday && (
          <div className="text-right">
            <p className="text-lg font-semibold tabular-nums">{formatMinutes(day.workedMinutes)}</p>
            <p className="text-xs text-slate-500">red. {formatMinutes(day.roundedMinutes)}</p>
          </div>
        )}
      </div>
      {workday && (
        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-600">
          <span>{LOCATION_LABELS[workday.location]}</span>
          {workday.breaks.length > 0 && <span>{breaksSummary(workday.breaks)}</span>}
          {label && <span>{label}</span>}
        </div>
      )}
      {day.warnings.length > 0 && (
        <ul className="mt-1 space-y-0.5 text-xs text-amber-800">
          {day.warnings.map((w, i) => (
            <li key={`${w.code}-${i}`}>⚠ {w.message}</li>
          ))}
        </ul>
      )}
    </>
  )

  const base = `block w-full rounded-xl border border-slate-200 text-left shadow-sm ${dayTone(day)} ${compact ? 'px-3 py-2' : 'p-3'}`
  if (outOfPeriod) {
    return <div className={base}>{content}</div>
  }
  return (
    <button
      type="button"
      className={`${base} hover:border-sky-300 focus-visible:outline-2 focus-visible:outline-sky-700`}
      onClick={() => onOpenDay(day.date)}
    >
      {content}
    </button>
  )
}
