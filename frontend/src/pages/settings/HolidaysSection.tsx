import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { errorMessage, isApiError } from '../../api/client'
import { periodsApi } from '../../api/endpoints'
import { invalidateDayData, queryKeys } from '../../api/queryKeys'
import type { HolidayScope, PeriodDto } from '../../api/types'
import { NoPeriod } from '../../components/NoPeriod'
import {
  Alert,
  Badge,
  Button,
  Card,
  FormErrors,
  QueryError,
  SelectField,
  Spinner,
  TextField,
} from '../../components/ui'
import { useSelectedPeriod } from '../../hooks/usePeriods'
import { capitalize, formatDateLong } from '../../lib/dates'
import { HOLIDAY_SCOPE_LABELS } from '../../lib/format'
import { applyServerErrors } from '../../lib/formErrors'

const SCOPES: HolidayScope[] = ['NACIONAL', 'AUTONOMICO', 'LOCAL', 'EMPRESA']

export function HolidaysSection() {
  const { periodsQuery, period } = useSelectedPeriod()
  return (
    <Card title={period ? `Festivos del periodo ${period.name}` : 'Festivos'} titleId="settings-holidays">
      {periodsQuery.isPending ? (
        <Spinner />
      ) : periodsQuery.isError ? (
        <QueryError error={periodsQuery.error} />
      ) : !period ? (
        <NoPeriod />
      ) : (
        <PeriodHolidays key={period.id} period={period} />
      )}
    </Card>
  )
}

function holidaySchema(period: PeriodDto) {
  return z.object({
    date: z
      .string()
      .min(1, 'Indica la fecha')
      .refine((d) => d >= period.startDate && d <= period.endDate, 'La fecha debe estar dentro del periodo'),
    name: z.string().trim().min(1, 'Indica el nombre').max(100, 'Máximo 100 caracteres'),
    scope: z.enum(['NACIONAL', 'AUTONOMICO', 'LOCAL', 'EMPRESA']),
  })
}

type HolidayValues = z.infer<ReturnType<typeof holidaySchema>>

function PeriodHolidays({ period }: { period: PeriodDto }) {
  const queryClient = useQueryClient()
  const [formErrors, setFormErrors] = useState<string[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const key = queryKeys.holidays(period.id)
  const query = useQuery({ queryKey: key, queryFn: () => periodsApi.holidays(period.id) })
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<HolidayValues>({
    resolver: zodResolver(holidaySchema(period)),
    defaultValues: { date: '', name: '', scope: 'LOCAL' },
  })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: key })
    void invalidateDayData(queryClient)
  }

  const add = useMutation({
    mutationFn: (values: HolidayValues) => periodsApi.addHoliday(period.id, values),
    onSuccess: (holiday) => {
      reset({ date: '', name: '', scope: holiday.scope })
      setMessage(`Festivo «${holiday.name}» añadido.`)
      refresh()
    },
    onError: (error) => {
      if (isApiError(error) && error.status === 409) {
        setError('date', { type: 'server', message: 'Ya hay un festivo en esa fecha' })
        return
      }
      setFormErrors(applyServerErrors(error, setError, ['date', 'name', 'scope']))
    },
  })

  const remove = useMutation({
    mutationFn: (holidayId: string) => periodsApi.removeHoliday(period.id, holidayId),
    onSuccess: refresh,
  })

  const preload = useMutation({
    mutationFn: () => periodsApi.preloadHolidays(period.id),
    onSuccess: (holidays) => {
      queryClient.setQueryData(key, holidays)
      setMessage('Festivos de Madrid precargados (solo se añaden los que faltaban).')
      void invalidateDayData(queryClient)
    },
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-slate-600">
          Festivos de {period.name}. Un festivo no es laborable y no suma horas teóricas.
        </p>
        <Button variant="secondary" size="sm" busy={preload.isPending} onClick={() => preload.mutate()}>
          Precargar festivos de Madrid
        </Button>
      </div>
      {message && <Alert tone="success">{message}</Alert>}
      {(remove.isError || preload.isError) && <Alert tone="error">{errorMessage(remove.error ?? preload.error)}</Alert>}

      <form
        onSubmit={handleSubmit((values) => {
          setFormErrors([])
          setMessage(null)
          add.mutate(values)
        })}
        noValidate
        className="grid gap-3 rounded-lg border border-slate-200 p-3 sm:grid-cols-[auto_1fr_auto_auto] sm:items-end"
        aria-label="Añadir festivo"
      >
        <TextField
          label="Fecha"
          type="date"
          min={period.startDate}
          max={period.endDate}
          error={errors.date?.message}
          {...register('date')}
        />
        <TextField label="Nombre" error={errors.name?.message} {...register('name')} />
        <SelectField label="Ámbito" error={errors.scope?.message} {...register('scope')}>
          {SCOPES.map((scope) => (
            <option key={scope} value={scope}>
              {HOLIDAY_SCOPE_LABELS[scope]}
            </option>
          ))}
        </SelectField>
        <Button type="submit" busy={add.isPending}>
          Añadir
        </Button>
        <div className="sm:col-span-4">
          <FormErrors messages={formErrors} />
        </div>
      </form>

      {query.isPending ? (
        <Spinner />
      ) : query.isError ? (
        <QueryError error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.length === 0 ? (
        <p className="text-sm text-slate-500">Sin festivos en este periodo.</p>
      ) : (
        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
          {query.data.map((holiday) => (
            <li key={holiday.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
              <div>
                <p className="text-sm font-medium text-slate-900">{holiday.name}</p>
                <p className="text-xs text-slate-600">{capitalize(formatDateLong(holiday.date))}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge
                  tone={
                    holiday.scope === 'NACIONAL'
                      ? 'red'
                      : holiday.scope === 'AUTONOMICO'
                        ? 'amber'
                        : holiday.scope === 'LOCAL'
                          ? 'violet'
                          : 'sky'
                  }
                >
                  {HOLIDAY_SCOPE_LABELS[holiday.scope]}
                </Badge>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-red-700"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(holiday.id)}
                  aria-label={`Borrar el festivo ${holiday.name}`}
                >
                  Borrar
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
