import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { meApi } from '../../api/endpoints'
import { invalidateDayData } from '../../api/queryKeys'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, Card, FormErrors, SelectField, TextField } from '../../components/ui'
import { ROLE_LABELS } from '../../lib/format'
import { applyServerErrors } from '../../lib/formErrors'
import { timeZones } from '../../lib/timezones'

const profileSchema = z.object({
  name: z.string().trim().min(1, 'Indica tu nombre').max(100, 'Máximo 100 caracteres'),
  company: z.string().trim().max(100, 'Máximo 100 caracteres'),
  timezone: z.string().min(1, 'Elige una zona horaria'),
})

type ProfileValues = z.infer<typeof profileSchema>

export function ProfileSection() {
  const { user, updateUser } = useAuth()
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)
  const [formErrors, setFormErrors] = useState<string[]>([])
  const zones = useMemo(() => timeZones(user?.timezone), [user?.timezone])
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      name: user?.name ?? '',
      company: user?.company ?? '',
      timezone: user?.timezone ?? 'Europe/Madrid',
    },
  })

  const save = useMutation({
    mutationFn: (values: ProfileValues) =>
      meApi.update({
        name: values.name,
        company: values.company === '' ? null : values.company,
        timezone: values.timezone,
      }),
    onSuccess: (updated) => {
      updateUser(updated)
      setSaved(true)
      // La zona horaria decide qué día es «hoy» en los resúmenes.
      void invalidateDayData(queryClient)
    },
    onError: (error) => setFormErrors(applyServerErrors(error, setError, ['name', 'company', 'timezone'])),
  })

  return (
    <Card title="Perfil" titleId="settings-profile">
      <form
        onSubmit={handleSubmit((values) => {
          setSaved(false)
          setFormErrors([])
          save.mutate(values)
        })}
        noValidate
        className="max-w-lg space-y-4"
        aria-label="Perfil"
      >
        {saved && <Alert tone="success">Perfil guardado.</Alert>}
        <FormErrors messages={formErrors} />
        <p className="text-sm text-slate-600">
          {user?.email} · {user ? ROLE_LABELS[user.role] : ''}
        </p>
        <TextField label="Nombre" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <TextField
          label="Empresa"
          autoComplete="organization"
          error={errors.company?.message}
          {...register('company')}
        />
        <SelectField
          label="Zona horaria"
          hint="Define qué día es «hoy» en tus cálculos."
          error={errors.timezone?.message}
          {...register('timezone')}
        >
          {zones.map((zone) => (
            <option key={zone} value={zone}>
              {zone}
            </option>
          ))}
        </SelectField>
        <Button type="submit" busy={save.isPending}>
          Guardar perfil
        </Button>
      </form>
    </Card>
  )
}
