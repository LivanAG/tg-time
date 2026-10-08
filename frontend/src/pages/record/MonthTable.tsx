import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Fragment, useMemo, useRef, useState, type FocusEvent, type KeyboardEvent } from 'react'

import { isApiError } from '../../api/client'
import { workdaysApi } from '../../api/endpoints'
import { invalidateDayData } from '../../api/queryKeys'
import type { DayDto, MonthSummaryDto, WorkLocation, WorkdayRequest } from '../../api/types'
import { Duration } from '../../components/Duration'
import { formatDayMonth, formatDayShort, todayIso } from '../../lib/dates'
import { LOCATION_LABELS } from '../../lib/format'
import { apiFieldToPath } from '../../lib/formErrors'
import { formatMinutes, normalizeTime, TIME_PATTERN } from '../../lib/time'
import { liveCalculation, mixedBounds, validateWorkday, type CalcRules, type CalcWorkday } from '../../lib/workdayCalc'
import { breaksSummary, dayLabel, dayTone, groupByWeek, isInlineEditable } from './dayInfo'

interface MonthTableProps {
  summary: MonthSummaryDto
  rules: CalcRules
  onOpenDay: (date: string) => void
  onNotice: (message: string) => void
}

const COLUMNS = 9

/** Hoja mensual como el Excel: semanas con subtotal y edición en línea guardada al salir de la fila. */
export function MonthTable({ summary, rules, onOpenDay, onNotice }: MonthTableProps) {
  const groups = groupByWeek(summary)
  const today = todayIso()

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
      <table className="w-full min-w-[820px] border-collapse text-sm">
        <caption className="sr-only">Registro diario del mes por semanas</caption>
        <thead className="bg-slate-100 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
          <tr>
            <th scope="col" className="px-3 py-2">
              Fecha
            </th>
            <th scope="col" className="px-2 py-2">
              Entrada
            </th>
            <th scope="col" className="px-2 py-2">
              Pausas
            </th>
            <th scope="col" className="px-2 py-2">
              Salida
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Total
            </th>
            <th scope="col" className="px-2 py-2 text-right">
              Redondeado
            </th>
            <th scope="col" className="px-2 py-2">
              Ubicación
            </th>
            <th scope="col" className="px-2 py-2">
              Check
            </th>
            <th scope="col" className="px-2 py-2">
              <span className="sr-only">Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {groups.map(({ week, days }) => (
            <Fragment key={week.weekStart}>
              {days.map((day) =>
                isInlineEditable(day) ? (
                  <EditableDayRow
                    // La fila se reinicia con los datos del servidor cuando cambia la versión del día.
                    key={`${day.date}-${day.workday?.version ?? 'nuevo'}`}
                    day={day}
                    periodId={summary.periodId}
                    rules={rules}
                    isToday={day.date === today}
                    onOpenDay={onOpenDay}
                    onNotice={onNotice}
                  />
                ) : (
                  <InfoDayRow key={day.date} day={day} isToday={day.date === today} onOpenDay={onOpenDay} />
                ),
              )}
              <tr
                className="border-y-2 border-slate-200 bg-slate-100 font-medium"
                aria-label={`Subtotal de la semana del ${formatDayMonth(week.weekStart)}`}
              >
                <th scope="row" colSpan={4} className="px-3 py-2 text-left text-slate-700">
                  Semana {formatDayMonth(week.weekStart)} – {formatDayMonth(week.weekEnd)}
                  <span className="ml-2 font-normal text-slate-500">
                    teóricas {formatMinutes(week.theoreticalMinutes)}
                  </span>
                </th>
                <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(week.workedMinutes)}</td>
                <td className="px-2 py-2 text-right tabular-nums">{formatMinutes(week.roundedMinutes)}</td>
                <td colSpan={COLUMNS - 6} className="px-2 py-2 text-slate-600">
                  Diferencia <Duration minutes={week.workedMinutes - week.theoreticalMinutes} />
                </td>
              </tr>
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function DateCell({ day, isToday }: { day: DayDto; isToday: boolean }) {
  return (
    <th scope="row" className="whitespace-nowrap px-3 py-1.5 text-left font-medium">
      <span className={isToday ? 'rounded bg-sky-700 px-1.5 py-0.5 text-white' : ''}>{formatDayShort(day.date)}</span>
      {day.intensive && day.dayType === 'LABORABLE' && (
        <span className="ml-1 text-xs font-normal text-amber-700" title="Jornada intensiva">
          7 h
        </span>
      )}
    </th>
  )
}

/** Fines de semana, festivos, ausencias de día completo y días fuera del periodo. */
function InfoDayRow({ day, isToday, onOpenDay }: { day: DayDto; isToday: boolean; onOpenDay: (date: string) => void }) {
  return (
    <tr className={`border-t border-slate-100 ${dayTone(day)}`}>
      <DateCell day={day} isToday={isToday} />
      <td colSpan={COLUMNS - 2} className="px-2 py-1.5 text-sm">
        {dayLabel(day)}
      </td>
      <td className="px-2 py-1.5 text-right">
        {day.dayType !== 'FUERA_DE_PERIODO' && (
          <button
            type="button"
            onClick={() => onOpenDay(day.date)}
            className="rounded px-2 py-1 text-xs font-medium text-sky-800 hover:bg-sky-50"
            aria-label={`Fichar el ${formatDayShort(day.date)}`}
          >
            Fichar
          </button>
        )}
      </td>
    </tr>
  )
}

/** Lo que se edita en la fila. En MIXTO, los dos tramos; si no, entrada y salida. */
interface Draft {
  startTime: string
  endTime: string
  location: WorkLocation
  officeStart: string
  officeEnd: string
  homeStart: string
  homeEnd: string
}

const TIME_KEYS = ['startTime', 'endTime', 'officeStart', 'officeEnd', 'homeStart', 'homeEnd'] as const
const MIXED_KEYS = ['officeStart', 'officeEnd', 'homeStart', 'homeEnd'] as const
/** Hueco fijo para el icono 🏢/🏠 de la columna Entrada (vacío en los días que no son mixtos). */
const ICON_SLOT = 'inline-block w-5 shrink-0 text-center'

function draftOf(day: DayDto): Draft {
  const w = day.workday
  return {
    startTime: w?.startTime ?? '',
    endTime: w?.endTime ?? '',
    location: w?.location ?? 'OFICINA',
    officeStart: w?.officeStart ?? '',
    officeEnd: w?.officeEnd ?? '',
    homeStart: w?.homeStart ?? '',
    homeEnd: w?.homeEnd ?? '',
  }
}

function sameDraft(a: Draft, b: Draft): boolean {
  return a.location === b.location && TIME_KEYS.every((key) => a[key] === b[key])
}

/** Al cambiar la ubicación se reaprovechan las horas: como en el editor completo. */
function withLocation(draft: Draft, location: WorkLocation): Draft {
  const next = { ...draft, location }
  if (location === 'MIXTO') {
    if (MIXED_KEYS.every((key) => draft[key] === '')) {
      next.officeStart = draft.startTime
      next.homeEnd = draft.endTime
    }
  } else if (draft.startTime === '' && draft.endTime === '') {
    const bounds = mixedBounds(draft)
    if (bounds) {
      next.startTime = bounds.startTime
      next.endTime = bounds.endTime
    }
  }
  return next
}

function calcInputOf(draft: Draft, breaks: CalcWorkday['breaks']): CalcWorkday {
  const mixed = draft.location === 'MIXTO'
  return {
    startTime: draft.startTime || null,
    endTime: draft.endTime || null,
    breaks,
    location: draft.location,
    mixed: mixed
      ? {
          officeStart: draft.officeStart || null,
          officeEnd: draft.officeEnd || null,
          homeStart: draft.homeStart || null,
          homeEnd: draft.homeEnd || null,
        }
      : null,
  }
}

type FieldErrors = Partial<Record<(typeof INLINE_FIELDS)[number], string>>

const INLINE_FIELDS = ['startTime', 'endTime', 'location', 'officeStart', 'officeEnd', 'homeStart', 'homeEnd'] as const

function isInlineField(field: string): field is (typeof INLINE_FIELDS)[number] {
  return (INLINE_FIELDS as readonly string[]).includes(field)
}

interface EditableDayRowProps {
  day: DayDto
  /** Periodo del mes mostrado: el fichaje se guarda en él. */
  periodId: string
  rules: CalcRules
  isToday: boolean
  onOpenDay: (date: string) => void
  onNotice: (message: string) => void
}

function EditableDayRow({ day, periodId, rules, isToday, onOpenDay, onNotice }: EditableDayRowProps) {
  const queryClient = useQueryClient()
  const initial = useMemo(() => draftOf(day), [day])
  const [draft, setDraft] = useState<Draft>(initial)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [rowErrors, setRowErrors] = useState<string[]>([])
  const [showWarnings, setShowWarnings] = useState(false)
  // Lo último guardado mientras llega el mes recalculado: evita reenviar lo mismo o con la versión vieja.
  const savedRef = useRef<{ draft: Draft; version: number } | null>(null)
  const workday = day.workday
  const dirty = !sameDraft(draft, initial)
  const label = formatDayShort(day.date)

  const save = useMutation({
    mutationFn: ({ body }: { body: WorkdayRequest; draft: Draft }) => workdaysApi.save(day.date, periodId, body),
    onSuccess: (saved, { draft: sent }) => {
      savedRef.current = { draft: sent, version: saved.version }
      setFieldErrors({})
      setRowErrors([])
      void invalidateDayData(queryClient)
    },
    onError: (error) => {
      if (isApiError(error) && error.status === 409) {
        onNotice(`El ${label} ha cambiado en otra pestaña o dispositivo: se han recargado los datos.`)
        void invalidateDayData(queryClient)
        return
      }
      if (isApiError(error) && error.errors.length > 0) {
        const fields: FieldErrors = {}
        const rest: string[] = []
        for (const e of error.errors) {
          const path = apiFieldToPath(e.field)
          if (isInlineField(path)) {
            fields[path] = e.message
          } else {
            rest.push(e.message)
          }
        }
        setFieldErrors(fields)
        setRowErrors(rest)
        return
      }
      setRowErrors([isApiError(error) ? error.detail : 'No se ha podido guardar el día'])
    },
  })

  const mixed = draft.location === 'MIXTO'
  const calcInput = calcInputOf(draft, workday?.breaks ?? [])

  const commit = () => {
    const baseline = savedRef.current?.draft ?? initial
    if (sameDraft(draft, baseline) || save.isPending) {
      return
    }
    const timeKeys = mixed ? MIXED_KEYS : (['startTime', 'endTime'] as const)
    // Un día sin fichar y sin horas no se crea (p. ej. solo se ha tocado la ubicación).
    if (!workday && timeKeys.every((key) => draft[key] === '')) {
      return
    }
    const fields: FieldErrors = {}
    const rest: string[] = []
    for (const key of timeKeys) {
      if (draft[key] && !TIME_PATTERN.test(draft[key])) {
        fields[key] = 'Hora no válida (HH:mm)'
      }
    }
    if (Object.keys(fields).length === 0) {
      for (const issue of validateWorkday(calcInput, rules)) {
        if (issue.field && isInlineField(issue.field)) {
          fields[issue.field] = issue.message
        } else {
          rest.push(issue.message)
        }
      }
    }
    setFieldErrors(fields)
    setRowErrors(rest)
    if (Object.keys(fields).length > 0 || rest.length > 0) {
      return
    }
    // En MIXTO, la entrada y la salida del día son la primera y la última de los tramos.
    const bounds = mixed ? mixedBounds(draft) : null
    save.mutate({
      draft,
      body: {
        startTime: normalizeTime(bounds?.startTime ?? draft.startTime),
        endTime: normalizeTime(bounds?.endTime ?? draft.endTime),
        breaks: workday?.breaks ?? [],
        location: draft.location,
        officeStart: mixed ? normalizeTime(draft.officeStart) : null,
        officeEnd: mixed ? normalizeTime(draft.officeEnd) : null,
        homeStart: mixed ? normalizeTime(draft.homeStart) : null,
        homeEnd: mixed ? normalizeTime(draft.homeEnd) : null,
        notes: workday?.notes ?? null,
        version: savedRef.current?.version ?? workday?.version ?? null,
      },
    })
  }

  // Guardado al salir de la fila (el foco pasa fuera de ella).
  const onRowBlur = (event: FocusEvent<HTMLTableRowElement>) => {
    const next = event.relatedTarget as Node | null
    if (next && event.currentTarget.contains(next)) {
      return
    }
    commit()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement | HTMLSelectElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      commit()
    } else if (event.key === 'Escape') {
      setDraft(initial)
      setFieldErrors({})
      setRowErrors([])
    }
  }

  const live = dirty ? liveCalculation(calcInput, rules).result : null
  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }))
  // Reparto oficina/casa de un día mixto: el recalculado si hay cambios, si no el guardado.
  const split = live ?? (workday?.location === 'MIXTO' ? workday.totals : null)
  const timeInput = (key: (typeof TIME_KEYS)[number], ariaLabel: string) => (
    <input
      type="time"
      aria-label={`${ariaLabel} del ${label}`}
      aria-invalid={fieldErrors[key] ? true : undefined}
      value={draft[key]}
      onChange={(e) => set({ [key]: e.target.value })}
      onKeyDown={onKeyDown}
      className={`${inputClass(fieldErrors[key])} w-[6.5rem]`}
    />
  )
  const inputClass = (error?: string) =>
    `rounded border px-1.5 py-1 text-sm tabular-nums focus:border-sky-600 focus:outline-none focus:ring-2 focus:ring-sky-600/30 ${
      error ? 'border-red-500' : 'border-slate-300'
    }`
  const allErrors = [...Object.values(fieldErrors), ...rowErrors]
  const warnings = day.warnings

  return (
    <>
      <tr
        className={`border-t border-slate-100 ${dayTone(day)}`}
        onBlur={onRowBlur}
        aria-busy={save.isPending || undefined}
      >
        <DateCell day={day} isToday={isToday} />
        <td className="px-2 py-1.5">
          {/* Todas las filas reservan el hueco del icono: así las entradas quedan alineadas. */}
          {mixed ? (
            <div className="space-y-1">
              <div className="flex items-center gap-1">
                <span aria-hidden="true" title="Oficina" className={ICON_SLOT}>
                  🏢
                </span>
                {timeInput('officeStart', 'Entrada en la oficina')}
              </div>
              <div className="flex items-center gap-1">
                <span aria-hidden="true" title="Casa" className={ICON_SLOT}>
                  🏠
                </span>
                {timeInput('homeStart', 'Entrada en casa')}
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-1">
              <span aria-hidden="true" className={ICON_SLOT} />
              {timeInput('startTime', 'Entrada')}
            </div>
          )}
        </td>
        <td className="max-w-[14rem] px-2 py-1.5 text-xs text-slate-600">
          {workday && workday.breaks.length > 0 ? (
            breaksSummary(workday.breaks)
          ) : (
            <span className="text-slate-400">—</span>
          )}
          {day.absence && <span className="block text-emerald-800">{dayLabel(day)}</span>}
        </td>
        <td className="px-2 py-1.5">
          {mixed ? (
            <div className="flex flex-col items-start gap-1">
              {timeInput('officeEnd', 'Salida de la oficina')}
              {timeInput('homeEnd', 'Salida de casa')}
            </div>
          ) : (
            timeInput('endTime', 'Salida')
          )}
        </td>
        <td className="px-2 py-1.5 text-right font-medium tabular-nums">
          {live ? (
            <span className="italic text-sky-800" title="Sin guardar">
              {formatMinutes(live.workedMinutes)}
            </span>
          ) : workday ? (
            formatMinutes(day.workedMinutes)
          ) : (
            <span className="text-slate-400">—</span>
          )}
        </td>
        <td className="px-2 py-1.5 text-right tabular-nums">
          {workday ? formatMinutes(day.roundedMinutes) : <span className="text-slate-400">—</span>}
        </td>
        <td className="px-2 py-1.5">
          <select
            aria-label={`Ubicación del ${label}`}
            value={draft.location}
            onChange={(e) => setDraft((d) => withLocation(d, e.target.value as WorkLocation))}
            onKeyDown={onKeyDown}
            className={`${inputClass(fieldErrors.location)} bg-white`}
          >
            {(['OFICINA', 'CASA', 'MIXTO'] as const).map((value) => (
              <option key={value} value={value}>
                {LOCATION_LABELS[value]}
              </option>
            ))}
          </select>
          {mixed && split && (
            <p className="mt-1 whitespace-nowrap text-xs text-slate-600 tabular-nums">
              oficina {formatMinutes(split.officeMinutes)} · casa {formatMinutes(split.remoteMinutes)}
            </p>
          )}
        </td>
        <td className="px-2 py-1.5">
          {save.isPending ? (
            <span className="text-xs text-slate-500">Guardando…</span>
          ) : warnings.length > 0 ? (
            <button
              type="button"
              onClick={() => setShowWarnings((v) => !v)}
              aria-expanded={showWarnings}
              aria-label={`${warnings.length} ${warnings.length === 1 ? 'aviso' : 'avisos'} del ${label}`}
              title={warnings.map((w) => w.message).join('\n')}
              className="rounded px-1.5 py-0.5 text-xs font-semibold text-amber-800 hover:bg-amber-100"
            >
              ⚠ {warnings.length}
            </button>
          ) : workday ? (
            <span className="text-emerald-700" aria-label="Sin avisos" title="Sin avisos">
              ✓
            </span>
          ) : null}
        </td>
        <td className="px-2 py-1.5 text-right">
          <button
            type="button"
            onClick={() => onOpenDay(day.date)}
            className="rounded px-2 py-1 text-xs font-medium text-sky-800 hover:bg-sky-50"
            aria-label={`Abrir el editor del ${label}`}
          >
            Editar
          </button>
        </td>
      </tr>
      {allErrors.length > 0 && (
        <tr className="bg-red-50">
          <td colSpan={COLUMNS} className="px-3 py-1.5 text-sm text-red-800" role="alert">
            {allErrors.join(' · ')}
          </td>
        </tr>
      )}
      {showWarnings && warnings.length > 0 && (
        <tr className="bg-amber-50">
          <td colSpan={COLUMNS} className="px-3 py-1.5">
            <ul className="space-y-0.5 text-sm text-amber-900">
              {warnings.map((w, i) => (
                <li key={`${w.code}-${i}`}>⚠ {w.message}</li>
              ))}
            </ul>
          </td>
        </tr>
      )}
    </>
  )
}
