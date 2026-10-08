import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useFieldArray, useForm, useWatch, type Control } from 'react-hook-form'
import { Link } from 'react-router'

import { isApiError } from '../../api/client'
import { absencesApi, workdaysApi } from '../../api/endpoints'
import { invalidateDayData, queryKeys } from '../../api/queryKeys'
import type { AbsenceDto, BreakType, IsoDate, WorkdayDto, WorkLocation } from '../../api/types'
import { Duration } from '../../components/Duration'
import { Modal } from '../../components/Modal'
import { Alert, Button, FormErrors, QueryError, Spinner, TextAreaField, TextField } from '../../components/ui'
import { useSelectedPeriod } from '../../hooks/usePeriods'
import { capitalize, formatDateLong } from '../../lib/dates'
import { absenceLabel, BREAK_LABELS, LOCATION_LABELS } from '../../lib/format'
import { applyServerErrors } from '../../lib/formErrors'
import { formatMinutes } from '../../lib/time'
import { liveCalculation, mixedBounds, type CalcRules } from '../../lib/workdayCalc'
import {
  NOTES_MAX,
  toCalcInput,
  toFormValues,
  toRequest,
  workdaySchema,
  WORKDAY_FIELDS,
  type WorkdayFormValues,
} from './workdayForm'

/** Valores del Excel, por si la fecha no cae en ningún periodo (solo para el total en vivo). */
const DEFAULT_RULES: CalcRules = { breakfastToleranceMin: 20, minLunchMin: 30 }

const CONFLICT_MESSAGE =
  'Este día ha cambiado en otra pestaña o dispositivo. Se han cargado los datos actuales: revísalos y vuelve a guardar.'

interface WorkdayEditorProps {
  date: IsoDate
  onClose: () => void
}

/**
 * Editor completo de un día (modal): horas, pausas, ubicación y notas con total en vivo. Trabaja sobre
 * el periodo seleccionado (cada periodo tiene sus propios fichajes).
 */
export function WorkdayEditor({ date, onClose }: WorkdayEditorProps) {
  const queryClient = useQueryClient()
  const { periodsQuery, period } = useSelectedPeriod()
  // Solo se lee y se ficha si el día está dentro del periodo seleccionado.
  const periodId = period && period.startDate <= date && date <= period.endDate ? period.id : null
  const workdayQuery = useQuery({
    queryKey: queryKeys.workday(date, periodId ?? ''),
    queryFn: () => workdaysApi.get(date, periodId as string),
    enabled: periodId !== null,
  })
  const absenceQuery = useQuery({
    queryKey: queryKeys.absence(date, periodId ?? ''),
    queryFn: () => absencesApi.get(date, periodId as string),
    enabled: periodId !== null,
  })
  const [notice, setNotice] = useState<string | null>(null)

  const breakfastToleranceMin = period?.breakfastToleranceMin ?? DEFAULT_RULES.breakfastToleranceMin
  const minLunchMin = period?.minLunchMin ?? DEFAULT_RULES.minLunchMin
  const rules = useMemo<CalcRules>(() => ({ breakfastToleranceMin, minLunchMin }), [breakfastToleranceMin, minLunchMin])

  const handleConflict = () => {
    setNotice(CONFLICT_MESSAGE)
    void queryClient.invalidateQueries({ queryKey: queryKeys.workday(date, periodId ?? '') })
    void invalidateDayData(queryClient)
  }

  const loading = periodsQuery.isPending || (periodId !== null && (workdayQuery.isPending || absenceQuery.isPending))
  const error = periodsQuery.error ?? workdayQuery.error

  return (
    <Modal
      title={capitalize(formatDateLong(date))}
      description={workdayQuery.data ? 'Editar el fichaje del día' : 'Nuevo fichaje'}
      onClose={onClose}
      size="lg"
    >
      {loading ? (
        <Spinner label="Cargando el día…" />
      ) : error ? (
        <QueryError error={error} onRetry={() => void workdayQuery.refetch()} />
      ) : (
        <WorkdayForm
          key={workdayQuery.data?.version ?? 'nuevo'}
          date={date}
          workday={workdayQuery.data ?? null}
          absence={absenceQuery.data ?? null}
          rules={rules}
          periodId={periodId}
          periodName={period?.name ?? null}
          notice={notice}
          onConflict={handleConflict}
          onClose={onClose}
        />
      )}
    </Modal>
  )
}

interface WorkdayFormProps {
  date: IsoDate
  workday: WorkdayDto | null
  absence: AbsenceDto | null
  rules: CalcRules
  /** Periodo en el que se guarda; null si el día no está en el periodo seleccionado (o no hay ninguno). */
  periodId: string | null
  periodName: string | null
  notice: string | null
  onConflict: () => void
  onClose: () => void
}

function WorkdayForm({
  date,
  workday,
  absence,
  rules,
  periodId,
  periodName,
  notice,
  onConflict,
  onClose,
}: WorkdayFormProps) {
  const queryClient = useQueryClient()
  const schema = useMemo(() => workdaySchema(rules), [rules])
  const [formErrors, setFormErrors] = useState<string[]>([])
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const confirmRef = useRef<HTMLButtonElement>(null)

  const {
    register,
    control,
    handleSubmit,
    setValue,
    getValues,
    setError,
    formState: { errors },
  } = useForm<WorkdayFormValues>({ resolver: zodResolver(schema), defaultValues: toFormValues(workday) })
  const { fields, append, remove } = useFieldArray({ control, name: 'breaks' })
  const location = useWatch({ control, name: 'location' })
  const breaks = useWatch({ control, name: 'breaks' })
  const notes = useWatch({ control, name: 'notes' })

  useEffect(() => {
    if (confirmingDelete) {
      confirmRef.current?.focus()
    }
  }, [confirmingDelete])

  const save = useMutation({
    mutationFn: (values: WorkdayFormValues) =>
      workdaysApi.save(date, periodId as string, toRequest(values, workday?.version ?? null)),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.workday(date, periodId as string), saved)
      void invalidateDayData(queryClient)
      onClose()
    },
    onError: (error) => {
      if (isApiError(error) && error.status === 409) {
        onConflict()
        return
      }
      setFormErrors(applyServerErrors(error, setError, WORKDAY_FIELDS))
    },
  })

  const removeDay = useMutation({
    mutationFn: () => workdaysApi.remove(date, periodId as string),
    onSuccess: () => {
      queryClient.setQueryData(queryKeys.workday(date, periodId as string), null)
      void invalidateDayData(queryClient)
      onClose()
    },
    onError: (error) => {
      if (isApiError(error) && error.status === 404) {
        queryClient.setQueryData(queryKeys.workday(date, periodId as string), null)
        void invalidateDayData(queryClient)
        onClose()
        return
      }
      setConfirmingDelete(false)
      setFormErrors(applyServerErrors(error, setError, WORKDAY_FIELDS))
    },
  })

  const onSubmit = handleSubmit((values) => {
    setFormErrors([])
    save.mutate(values)
  })

  // Al pasar a MIXTO, la entrada del día se propone como entrada en la oficina y la salida como salida de
  // casa ("teletrabajo tardes"); al salir de MIXTO, si no hay entrada/salida, se toman de los tramos.
  const adaptTimesTo = (next: WorkLocation) => {
    const values = getValues()
    const options = { shouldDirty: true, shouldValidate: false }
    if (next === 'MIXTO') {
      if (!values.officeStart && !values.homeStart && !values.officeEnd && !values.homeEnd) {
        setValue('officeStart', values.startTime, options)
        setValue('homeEnd', values.endTime, options)
      }
      return
    }
    const bounds = mixedBounds(toCalcInput({ ...values, location: 'MIXTO' }).mixed)
    if (bounds && !values.startTime && !values.endTime) {
      setValue('startTime', bounds.startTime, options)
      setValue('endTime', bounds.endTime, options)
    }
  }

  const hasBreakfast = (breaks ?? []).some((b) => b.type === 'DESAYUNO')
  const hasLunch = (breaks ?? []).some((b) => b.type === 'COMIDA')
  const addBreak = (type: BreakType) => append({ type, startTime: '', endTime: '' })
  const busy = save.isPending || removeDay.isPending
  const breaksError = errors.breaks?.message ?? errors.breaks?.root?.message

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5" aria-label="Fichaje del día">
      {notice && <Alert tone="warning">{notice}</Alert>}
      {absence && (
        <Alert tone="info">
          Este día tiene una ausencia: <strong>{absenceLabel(absence.type, absence.halfDay)}</strong>
          {absence.note ? ` (${absence.note})` : ''}.
        </Alert>
      )}
      {periodId === null &&
        (periodName ? (
          <Alert tone="warning" title={`Este día no está en el periodo ${periodName}`}>
            Cada periodo tiene sus propios fichajes. Para fichar este día, elige en el selector de periodo uno que
            lo incluya, o créalo en{' '}
            <Link to="/ajustes?seccion=periodos" className="font-medium underline" onClick={onClose}>
              Ajustes
            </Link>
            .
          </Alert>
        ) : (
          <Alert tone="warning" title="Aún no tienes ningún periodo">
            Para fichar, crea antes un periodo en{' '}
            <Link to="/ajustes?seccion=periodos" className="font-medium underline" onClick={onClose}>
              Ajustes
            </Link>
            .
          </Alert>
        ))}
      <FormErrors messages={formErrors} />

      <fieldset className="space-y-2">
        <legend className="text-sm font-semibold text-slate-800">Ubicación</legend>
        <div className="grid grid-cols-3 gap-2">
          {(['OFICINA', 'CASA', 'MIXTO'] as const).map((value) => (
            <label
              key={value}
              className="flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm has-[:checked]:border-sky-700 has-[:checked]:bg-sky-50 has-[:checked]:font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-sky-700"
            >
              <input
                type="radio"
                value={value}
                className="sr-only"
                {...register('location', { onChange: (e) => adaptTimesTo(e.target.value as WorkLocation) })}
              />
              {LOCATION_LABELS[value]}
            </label>
          ))}
        </div>
      </fieldset>

      {location === 'MIXTO' ? (
        <div className="space-y-3">
          {MIXED_SEGMENTS.map((segment) => (
            <fieldset key={segment.title} className="rounded-lg border border-slate-200 p-3">
              <legend className="px-1 text-sm font-semibold text-slate-800">
                <span aria-hidden="true">{segment.icon}</span> {segment.title}
              </legend>
              <div className="grid grid-cols-2 gap-3">
                <TextField
                  type="time"
                  label={segment.startLabel}
                  error={errors[segment.start]?.message}
                  {...register(segment.start)}
                />
                <TextField
                  type="time"
                  label={segment.endLabel}
                  error={errors[segment.end]?.message}
                  {...register(segment.end)}
                />
              </div>
            </fieldset>
          ))}
          <p className="text-xs text-slate-500">
            Los tramos pueden ir en cualquier orden. El hueco entre los dos (desplazamiento, comida...) no cuenta
            como trabajado; las pausas van dentro de un tramo.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <TextField type="time" label="Entrada" error={errors.startTime?.message} {...register('startTime')} />
          <TextField type="time" label="Salida" error={errors.endTime?.message} {...register('endTime')} />
        </div>
      )}

      <fieldset className="space-y-3">
        <legend className="text-sm font-semibold text-slate-800">Pausas</legend>
        {fields.length === 0 && <p className="text-sm text-slate-500">Sin pausas.</p>}
        {fields.map((field, index) => {
          const itemErrors = errors.breaks?.[index]
          return (
            <div key={field.id} className="rounded-lg border border-slate-200 p-3">
              <div className="grid grid-cols-[1fr_1fr] gap-3 sm:grid-cols-[1.2fr_1fr_1fr_auto] sm:items-end">
                <div className="col-span-2 space-y-1 sm:col-span-1">
                  <label htmlFor={`break-${index}-type`} className="block text-sm font-medium text-slate-700">
                    Tipo de la pausa {index + 1}
                  </label>
                  <select
                    id={`break-${index}-type`}
                    className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-base sm:text-sm"
                    {...register(`breaks.${index}.type`)}
                  >
                    {(['DESAYUNO', 'COMIDA', 'OTRA'] as const).map((type) => (
                      <option key={type} value={type}>
                        {BREAK_LABELS[type]}
                      </option>
                    ))}
                  </select>
                </div>
                <TextField
                  type="time"
                  label={`Inicio de la pausa ${index + 1}`}
                  error={itemErrors?.startTime?.message}
                  {...register(`breaks.${index}.startTime`)}
                />
                <TextField
                  type="time"
                  label={`Fin de la pausa ${index + 1}`}
                  error={itemErrors?.endTime?.message}
                  {...register(`breaks.${index}.endTime`)}
                />
                <Button
                  variant="ghost"
                  size="sm"
                  className="col-span-2 justify-self-end sm:col-span-1"
                  onClick={() => remove(index)}
                  aria-label={`Quitar la pausa ${index + 1}`}
                >
                  Quitar
                </Button>
              </div>
              {itemErrors?.message && (
                <p className="mt-2 text-sm text-red-700" role="alert">
                  {itemErrors.message}
                </p>
              )}
            </div>
          )
        })}
        {breaksError && (
          <p className="text-sm text-red-700" role="alert">
            {breaksError}
          </p>
        )}
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" size="sm" onClick={() => addBreak('DESAYUNO')} disabled={hasBreakfast}>
            + Desayuno
          </Button>
          <Button variant="secondary" size="sm" onClick={() => addBreak('COMIDA')} disabled={hasLunch}>
            + Comida
          </Button>
          <Button variant="secondary" size="sm" onClick={() => addBreak('OTRA')}>
            + Otra pausa
          </Button>
        </div>
      </fieldset>

      <TextAreaField
        label="Notas"
        rows={3}
        maxLength={NOTES_MAX}
        hint={`${(notes ?? '').length}/${NOTES_MAX} caracteres`}
        error={errors.notes?.message}
        {...register('notes')}
      />

      <LiveTotal control={control} rules={rules} />

      <div className="sticky bottom-0 -mx-4 -mb-4 flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 bg-white px-4 py-3">
        {workday && !confirmingDelete && (
          <Button
            variant="ghost"
            className="text-red-700 hover:bg-red-50"
            onClick={() => setConfirmingDelete(true)}
            disabled={busy}
          >
            Borrar día
          </Button>
        )}
        {confirmingDelete && (
          <div role="alertdialog" aria-label="Confirmar borrado" className="flex flex-wrap items-center gap-2">
            <span className="text-sm text-slate-700">¿Borrar el fichaje de este día? No se puede deshacer.</span>
            <Button
              ref={confirmRef}
              variant="danger"
              size="sm"
              busy={removeDay.isPending}
              onClick={() => removeDay.mutate()}
            >
              Sí, borrar
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setConfirmingDelete(false)}>
              Cancelar
            </Button>
          </div>
        )}
        <div className="ml-auto flex gap-2">
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button type="submit" busy={save.isPending} disabled={periodId === null || removeDay.isPending}>
            Guardar
          </Button>
        </div>
      </div>
    </form>
  )
}

/** Los dos tramos de un día mixto en el editor. */
const MIXED_SEGMENTS = [
  {
    icon: '🏢',
    title: 'Oficina',
    start: 'officeStart',
    end: 'officeEnd',
    startLabel: 'Entrada en la oficina',
    endLabel: 'Salida de la oficina',
  },
  {
    icon: '🏠',
    title: 'Casa',
    start: 'homeStart',
    end: 'homeEnd',
    startLabel: 'Entrada en casa',
    endLabel: 'Salida de casa',
  },
] as const

/** Total del día recalculado en cada cambio con el mismo motor que el backend. */
function LiveTotal({ control, rules }: { control: Control<WorkdayFormValues>; rules: CalcRules }) {
  const values = useWatch({ control }) as WorkdayFormValues
  const { errors, result } = liveCalculation(toCalcInput({ ...values, breaks: values.breaks ?? [] }), rules)

  return (
    <section aria-label="Total del día" aria-live="polite" className="rounded-xl bg-slate-50 p-4">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-sm font-medium text-slate-600">Total trabajado</span>
        <span className="text-3xl font-bold tabular-nums text-slate-900" data-testid="live-total">
          {result ? formatMinutes(result.workedMinutes) : '—'}
        </span>
      </div>
      {!result && (
        <p className="mt-1 text-sm text-slate-600">
          {values.startTime && values.endTime ? errors[0]?.message : 'Indica la entrada y la salida para ver el total.'}
        </p>
      )}
      {result && (
        <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm text-slate-700 sm:grid-cols-3">
          <div>
            <dt className="inline text-slate-500">Jornada bruta: </dt>
            <dd className="inline tabular-nums">{formatMinutes(result.grossMinutes)}</dd>
          </div>
          <div>
            <dt className="inline text-slate-500">Desayuno: </dt>
            <dd className="inline tabular-nums">
              {formatMinutes(result.breakfastMinutes)} (descuenta {formatMinutes(result.breakfastDeductedMinutes)})
            </dd>
          </div>
          <div>
            <dt className="inline text-slate-500">Comida: </dt>
            <dd className="inline tabular-nums">
              {formatMinutes(result.lunchMinutes)} (descuenta {formatMinutes(result.lunchDeductedMinutes)})
            </dd>
          </div>
          <div>
            <dt className="inline text-slate-500">Otras pausas: </dt>
            <dd className="inline tabular-nums">{formatMinutes(result.otherBreakMinutes)}</dd>
          </div>
          <div>
            <dt className="inline text-slate-500">Oficina: </dt>
            <dd className="inline">
              <Duration minutes={result.officeMinutes} signed={false} />
            </dd>
          </div>
          <div>
            <dt className="inline text-slate-500">Casa: </dt>
            <dd className="inline">
              <Duration minutes={result.remoteMinutes} signed={false} />
            </dd>
          </div>
        </dl>
      )}
      {result && result.warnings.length > 0 && (
        <ul className="mt-3 space-y-1">
          {result.warnings.map((w) => (
            <li key={w.code} className="text-sm text-amber-800">
              ⚠ {w.message}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
