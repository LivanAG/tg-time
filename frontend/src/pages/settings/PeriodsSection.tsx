import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router'

import { isApiError } from '../../api/client'
import { periodsApi } from '../../api/endpoints'
import { invalidatePeriodData } from '../../api/queryKeys'
import type { PeriodDto } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Alert, Button, Card, CheckboxField, FormErrors, QueryError, Spinner, TextField } from '../../components/ui'
import { usePeriods } from '../../hooks/usePeriods'
import { formatDate } from '../../lib/dates'
import { applyServerErrors } from '../../lib/formErrors'
import { formatMinutes } from '../../lib/time'
import {
  newPeriodDefaults,
  PERIOD_FIELDS,
  periodSchema,
  toCreateRequest,
  toPeriodFormValues,
  toUpdateRequest,
  type PeriodFormValues,
} from './periodForm'

type Editing = { mode: 'create' } | { mode: 'edit'; period: PeriodDto }

export function PeriodsSection() {
  const queryClient = useQueryClient()
  const periodsQuery = usePeriods()
  const [params, setParams] = useSearchParams()
  const [editing, setEditing] = useState<Editing | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  // ?nuevo=1 (desde el asistente de bienvenida) abre directamente el alta.
  const wantsNew = params.get('nuevo') === '1'
  const creating = editing ?? (wantsNew && periodsQuery.isSuccess ? ({ mode: 'create' } as const) : null)

  const closeForm = () => {
    setEditing(null)
    if (wantsNew) {
      const next = new URLSearchParams(params)
      next.delete('nuevo')
      setParams(next, { replace: true })
    }
  }

  const remove = useMutation({
    mutationFn: (id: string) => periodsApi.remove(id),
    onSuccess: () => {
      setConfirmDelete(null)
      setMessage('Periodo borrado. Los fichajes y ausencias se conservan.')
      void invalidatePeriodData(queryClient)
    },
  })

  const periods = periodsQuery.data ?? []

  return (
    <Card
      title="Periodos"
      titleId="settings-periods"
      actions={
        <Button size="sm" onClick={() => setEditing({ mode: 'create' })} disabled={!periodsQuery.isSuccess}>
          Nuevo periodo
        </Button>
      }
    >
      <div className="space-y-3">
        <p className="text-sm text-slate-600">
          Cada periodo equivale a la hoja Horas del Excel: fechas, convenio, vacaciones, jornadas, intensiva y límites.
        </p>
        {message && <Alert tone="success">{message}</Alert>}
        {remove.isError && <Alert tone="error">{remove.error.message}</Alert>}
        {periodsQuery.isPending && <Spinner />}
        {periodsQuery.isError && <QueryError error={periodsQuery.error} onRetry={() => void periodsQuery.refetch()} />}
        {periodsQuery.isSuccess && periods.length === 0 && (
          <Alert tone="info">
            Aún no hay periodos. Pulsa «Nuevo periodo»: el formulario trae los valores de tu Excel.
          </Alert>
        )}
        <ul className="space-y-3">
          {periods.map((period) => (
            <li key={period.id} className="rounded-lg border border-slate-200 p-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold text-slate-900">{period.name}</p>
                  <p className="text-sm text-slate-600">
                    {formatDate(period.startDate)} – {formatDate(period.endDate)}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setEditing({ mode: 'edit', period })}
                    aria-label={`Editar el periodo ${period.name}`}
                  >
                    Editar
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-red-700"
                    onClick={() => setConfirmDelete(period.id)}
                    aria-label={`Borrar el periodo ${period.name}`}
                  >
                    Borrar
                  </Button>
                </div>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-sm text-slate-700 sm:grid-cols-4">
                <div>
                  <dt className="inline text-slate-500">Convenio: </dt>
                  <dd className="inline">{formatMinutes(period.agreementMinutes)} h</dd>
                </div>
                <div>
                  <dt className="inline text-slate-500">Vacaciones: </dt>
                  <dd className="inline">{period.vacationDays} días</dd>
                </div>
                <div>
                  <dt className="inline text-slate-500">Jornada: </dt>
                  <dd className="inline">
                    {formatMinutes(period.normalDayMinutes)} / {formatMinutes(period.intensiveDayMinutes)}
                  </dd>
                </div>
                <div>
                  <dt className="inline text-slate-500">Teletrabajo: </dt>
                  <dd className="inline">máx. {period.maxRemotePct} %</dd>
                </div>
                <div className="col-span-2 sm:col-span-4">
                  <dt className="inline text-slate-500">Intensiva: </dt>
                  <dd className="inline">
                    {period.intensiveRanges.length === 0
                      ? 'sin rangos'
                      : period.intensiveRanges
                          .map((r) => `${formatDate(r.startDate)} – ${formatDate(r.endDate)}`)
                          .join(', ')}
                  </dd>
                </div>
              </dl>
              {confirmDelete === period.id && (
                <div
                  role="alertdialog"
                  aria-label="Confirmar borrado del periodo"
                  className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-900"
                >
                  <p>
                    ¿Borrar el periodo {period.name}? Se borran sus festivos y rangos de intensiva; los fichajes y las
                    ausencias se conservan.
                  </p>
                  <div className="mt-2 flex gap-2">
                    <Button variant="danger" size="sm" busy={remove.isPending} onClick={() => remove.mutate(period.id)}>
                      Sí, borrar
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => setConfirmDelete(null)}>
                      Cancelar
                    </Button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>
      {creating && (
        <PeriodFormDialog
          key={creating.mode === 'edit' ? creating.period.id : 'nuevo'}
          editing={creating}
          periods={periods}
          onClose={closeForm}
          onSaved={(text) => {
            setMessage(text)
            closeForm()
          }}
        />
      )}
    </Card>
  )
}

interface PeriodFormDialogProps {
  editing: Editing
  periods: PeriodDto[]
  onClose: () => void
  onSaved: (message: string) => void
}

function PeriodFormDialog({ editing, periods, onClose, onSaved }: PeriodFormDialogProps) {
  const queryClient = useQueryClient()
  const [formErrors, setFormErrors] = useState<string[]>([])
  const isEdit = editing.mode === 'edit'
  // La versión avanza si se guarda el periodo pero falla el guardado de los rangos.
  const versionRef = useRef(isEdit ? editing.period.version : 0)
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<PeriodFormValues>({
    resolver: zodResolver(periodSchema),
    defaultValues: isEdit ? toPeriodFormValues(editing.period) : newPeriodDefaults(periods),
  })
  const ranges = useFieldArray({ control, name: 'intensiveRanges' })

  const save = useMutation({
    mutationFn: async (values: PeriodFormValues) => {
      if (!isEdit) {
        return periodsApi.create(toCreateRequest(values))
      }
      const id = editing.period.id
      const updated = await periodsApi.update(id, toUpdateRequest(values, versionRef.current))
      versionRef.current = updated.version
      // PUT /periods/{id} ignora intensiveRanges: se guardan aparte (sustituye la lista).
      await periodsApi.saveIntensiveRanges(id, values.intensiveRanges)
      return updated
    },
    onSuccess: (period) => {
      void invalidatePeriodData(queryClient)
      onSaved(isEdit ? `Periodo ${period.name} guardado.` : `Periodo ${period.name} creado.`)
    },
    onError: (error) => {
      void invalidatePeriodData(queryClient)
      if (isApiError(error) && error.status === 409) {
        setFormErrors([
          isEdit
            ? 'El periodo ha cambiado o se solapa con otro. Cierra y vuelve a abrirlo para cargar los datos actuales.'
            : 'El periodo se solapa con otro que ya tienes.',
        ])
        return
      }
      setFormErrors(applyServerErrors(error, setError, PERIOD_FIELDS))
    },
  })

  const rangesError = errors.intensiveRanges?.message ?? errors.intensiveRanges?.root?.message

  return (
    <Modal
      title={isEdit ? `Editar ${editing.period.name}` : 'Nuevo periodo'}
      description="Los valores de la hoja Horas de tu Excel"
      onClose={onClose}
      size="xl"
    >
      <form
        onSubmit={handleSubmit((values) => {
          setFormErrors([])
          save.mutate(values)
        })}
        noValidate
        className="space-y-5"
        aria-label={isEdit ? 'Editar periodo' : 'Nuevo periodo'}
      >
        <FormErrors messages={formErrors} />
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Nombre" error={errors.name?.message} {...register('name')} />
          <TextField label="Fecha de inicio" type="date" error={errors.startDate?.message} {...register('startDate')} />
          <TextField label="Fecha de fin" type="date" error={errors.endDate?.message} {...register('endDate')} />
        </div>

        <fieldset className="grid gap-4 sm:grid-cols-3">
          <legend className="mb-2 text-sm font-semibold text-slate-800">Convenio y jornadas</legend>
          <TextField
            label="Horas de convenio (h:mm)"
            hint="1760 h = 1760:00"
            error={errors.agreementMinutes?.message}
            {...register('agreementMinutes')}
          />
          <TextField
            label="Días de vacaciones"
            inputMode="numeric"
            error={errors.vacationDays?.message}
            {...register('vacationDays')}
          />
          <TextField
            label="Saldo inicial (h:mm)"
            hint="Horas arrastradas de otro periodo; negativo con «-»"
            error={errors.openingBalanceMin?.message}
            {...register('openingBalanceMin')}
          />
          <TextField
            label="Jornada normal (h:mm)"
            error={errors.normalDayMinutes?.message}
            {...register('normalDayMinutes')}
          />
          <TextField
            label="Jornada intensiva (h:mm)"
            error={errors.intensiveDayMinutes?.message}
            {...register('intensiveDayMinutes')}
          />
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-3">
          <legend className="mb-2 text-sm font-semibold text-slate-800">Pausas, redondeo y teletrabajo</legend>
          <TextField
            label="Tolerancia de desayuno (min)"
            inputMode="numeric"
            error={errors.breakfastToleranceMin?.message}
            {...register('breakfastToleranceMin')}
          />
          <TextField
            label="Comida mínima (min)"
            inputMode="numeric"
            error={errors.minLunchMin?.message}
            {...register('minLunchMin')}
          />
          <TextField
            label="Tramo de redondeo (min)"
            inputMode="numeric"
            error={errors.roundingStepMin?.message}
            {...register('roundingStepMin')}
          />
          <TextField
            label="% máximo de teletrabajo"
            inputMode="numeric"
            error={errors.maxRemotePct?.message}
            {...register('maxRemotePct')}
          />
        </fieldset>

        <fieldset className="space-y-3">
          <legend className="text-sm font-semibold text-slate-800">Jornada intensiva (7 h)</legend>
          {ranges.fields.length === 0 && <p className="text-sm text-slate-500">Sin rangos de intensiva.</p>}
          {ranges.fields.map((field, index) => (
            <div key={field.id} className="grid grid-cols-[1fr_1fr_auto] items-end gap-3">
              <TextField
                label={`Inicio del rango ${index + 1}`}
                type="date"
                error={errors.intensiveRanges?.[index]?.startDate?.message}
                {...register(`intensiveRanges.${index}.startDate`)}
              />
              <TextField
                label={`Fin del rango ${index + 1}`}
                type="date"
                error={errors.intensiveRanges?.[index]?.endDate?.message}
                {...register(`intensiveRanges.${index}.endDate`)}
              />
              <Button
                variant="ghost"
                size="sm"
                onClick={() => ranges.remove(index)}
                aria-label={`Quitar el rango ${index + 1}`}
              >
                Quitar
              </Button>
            </div>
          ))}
          {rangesError && <p className="text-sm text-red-700">{rangesError}</p>}
          <Button variant="secondary" size="sm" onClick={() => ranges.append({ startDate: '', endDate: '' })}>
            + Añadir rango
          </Button>
        </fieldset>

        {!isEdit && (
          <CheckboxField
            label="Precargar los festivos de Madrid"
            hint="Nacionales, de la Comunidad de Madrid y locales del periodo. Luego puedes editarlos en Festivos."
            {...register('preloadHolidays')}
          />
        )}

        <div className="flex justify-end gap-2 border-t border-slate-200 pt-3">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" busy={save.isPending}>
            {isEdit ? 'Guardar' : 'Crear periodo'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
