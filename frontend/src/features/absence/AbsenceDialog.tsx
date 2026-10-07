import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { isApiError } from '../../api/client'
import { absencesApi } from '../../api/endpoints'
import { invalidateDayData } from '../../api/queryKeys'
import type { AbsenceDto, AbsenceType, IsoDate } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Button, CheckboxField, FormErrors, TextField } from '../../components/ui'
import { capitalize, formatDateLong } from '../../lib/dates'
import { ABSENCE_LABELS } from '../../lib/format'
import { applyServerErrors } from '../../lib/formErrors'
import { formatMinutes } from '../../lib/time'

const ABSENCE_TYPES: AbsenceType[] = ['VACACIONES', 'PUENTE', 'PERMISO', 'BAJA']

const ABSENCE_HINTS: Record<AbsenceType, string> = {
  VACACIONES: 'Descuenta la jornada de las horas teóricas y cuenta como día de vacaciones.',
  PUENTE: 'No se trabaja, pero las horas se restan del saldo: hay que recuperarlas.',
  PERMISO: 'Permiso retribuido: descuenta la jornada de las horas teóricas.',
  BAJA: 'Baja: descuenta la jornada de las horas teóricas.',
}

const absenceSchema = z.object({
  type: z.enum(['VACACIONES', 'PUENTE', 'PERMISO', 'BAJA']),
  halfDay: z.boolean(),
  note: z.string().max(500, 'Máximo 500 caracteres'),
})

type AbsenceValues = z.infer<typeof absenceSchema>

interface AbsenceDialogProps {
  date: IsoDate
  absence: AbsenceDto | null
  dayMinutes: number
  onClose: () => void
  /** Abre el editor del fichaje de ese día. */
  onOpenWorkday: () => void
}

/** Marcar o quitar una ausencia (vacaciones, puente, permiso o baja; día completo o medio día). */
export function AbsenceDialog({ date, absence, dayMinutes, onClose, onOpenWorkday }: AbsenceDialogProps) {
  const queryClient = useQueryClient()
  const [formErrors, setFormErrors] = useState<string[]>([])
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<AbsenceValues>({
    resolver: zodResolver(absenceSchema),
    defaultValues: {
      type: absence?.type ?? 'VACACIONES',
      halfDay: absence?.halfDay ?? false,
      note: absence?.note ?? '',
    },
  })

  const save = useMutation({
    mutationFn: (values: AbsenceValues) =>
      absencesApi.save(date, {
        type: values.type,
        halfDay: values.halfDay,
        note: values.note.trim() === '' ? null : values.note.trim(),
      }),
    onSuccess: () => {
      void invalidateDayData(queryClient)
      onClose()
    },
    onError: (error) => setFormErrors(applyServerErrors(error, setError, ['type', 'halfDay', 'note'])),
  })

  const remove = useMutation({
    mutationFn: () => absencesApi.remove(date),
    onSuccess: () => {
      void invalidateDayData(queryClient)
      onClose()
    },
    onError: (error) => {
      if (isApiError(error) && error.status === 404) {
        void invalidateDayData(queryClient)
        onClose()
        return
      }
      setFormErrors(applyServerErrors(error, setError, []))
    },
  })

  const busy = save.isPending || remove.isPending

  return (
    <Modal
      title={capitalize(formatDateLong(date))}
      description={`Día laborable de ${formatMinutes(dayMinutes)}. ${absence ? 'Tiene una ausencia marcada.' : 'Sin ausencia.'}`}
      onClose={onClose}
      size="md"
    >
      <form
        onSubmit={handleSubmit((values) => {
          setFormErrors([])
          save.mutate(values)
        })}
        noValidate
        className="space-y-4"
        aria-label="Ausencia del día"
      >
        <FormErrors messages={formErrors} />
        <fieldset className="space-y-2">
          <legend className="text-sm font-semibold text-slate-800">Tipo de ausencia</legend>
          {ABSENCE_TYPES.map((type) => (
            <label
              key={type}
              className="flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 p-2 has-[:checked]:border-sky-700 has-[:checked]:bg-sky-50"
            >
              <input type="radio" value={type} className="mt-1" {...register('type')} />
              <span>
                <span className="block text-sm font-medium text-slate-900">{ABSENCE_LABELS[type]}</span>
                <span className="block text-xs text-slate-600">{ABSENCE_HINTS[type]}</span>
              </span>
            </label>
          ))}
          {errors.type?.message && <p className="text-sm text-red-700">{errors.type.message}</p>}
        </fieldset>
        <CheckboxField
          label="Medio día"
          hint={`Cuenta la mitad de la jornada (${formatMinutes(Math.ceil(dayMinutes / 2))}).`}
          {...register('halfDay')}
        />
        <TextField
          label="Nota (opcional)"
          maxLength={500}
          autoComplete="off"
          error={errors.note?.message}
          {...register('note')}
        />

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 pt-3">
          <Button variant="ghost" onClick={onOpenWorkday} disabled={busy}>
            Ver el fichaje del día
          </Button>
          <div className="ml-auto flex flex-wrap gap-2">
            {absence && (
              <Button
                variant="secondary"
                className="text-red-700"
                busy={remove.isPending}
                disabled={save.isPending}
                onClick={() => remove.mutate()}
              >
                Quitar ausencia
              </Button>
            )}
            <Button type="submit" busy={save.isPending} disabled={remove.isPending}>
              {absence ? 'Guardar' : 'Marcar ausencia'}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  )
}
