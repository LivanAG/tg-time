import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { isApiError } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { queryKeys } from '../../api/queryKeys'
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
import { ROLE_LABELS } from '../../lib/format'
import { applyServerErrors } from '../../lib/formErrors'
import { passwordSchema } from '../../lib/password'
import { timeZones } from '../../lib/timezones'

const newUserSchema = z
  .object({
    name: z.string().trim().min(1, 'Indica el nombre').max(100, 'Máximo 100 caracteres'),
    email: z.string().trim().min(1, 'Indica el email').email('Email no válido').max(254, 'Máximo 254 caracteres'),
    password: z.string(),
    company: z.string().trim().max(100, 'Máximo 100 caracteres'),
    timezone: z.string().min(1, 'Elige una zona horaria'),
    role: z.enum(['USER', 'ADMIN']),
  })
  .superRefine((values, ctx) => {
    const result = passwordSchema(() => values.email).safeParse(values.password)
    if (!result.success) {
      ctx.addIssue({
        code: 'custom',
        path: ['password'],
        message: result.error.issues[0]?.message ?? 'Contraseña no válida',
      })
    }
  })

type NewUserValues = z.infer<typeof newUserSchema>

const EMPTY: NewUserValues = {
  name: '',
  email: '',
  password: '',
  company: 'IZERTIS',
  timezone: 'Europe/Madrid',
  role: 'USER',
}

/** Solo ADMIN: lista de usuarios y alta por invitación. */
export function AdminUsersSection() {
  const queryClient = useQueryClient()
  const zones = useMemo(() => timeZones(), [])
  const [formErrors, setFormErrors] = useState<string[]>([])
  const [created, setCreated] = useState<string | null>(null)
  const users = useQuery({ queryKey: queryKeys.adminUsers, queryFn: adminApi.listUsers })
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<NewUserValues>({ resolver: zodResolver(newUserSchema), defaultValues: EMPTY })

  const create = useMutation({
    mutationFn: (values: NewUserValues) =>
      adminApi.createUser({ ...values, company: values.company === '' ? null : values.company }),
    onSuccess: (user) => {
      setCreated(`Usuario ${user.email} creado. Pásale la contraseña por un canal seguro.`)
      reset(EMPTY)
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminUsers })
    },
    onError: (error) => {
      if (isApiError(error) && error.status === 409) {
        setError('email', { type: 'server', message: 'Ya existe un usuario con ese email' })
        return
      }
      setFormErrors(applyServerErrors(error, setError, ['name', 'email', 'password', 'company', 'timezone', 'role']))
    },
  })

  return (
    <div className="space-y-4">
      <Card title="Usuarios" titleId="settings-users">
        {users.isPending ? (
          <Spinner />
        ) : users.isError ? (
          <QueryError error={users.error} onRetry={() => void users.refetch()} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] text-sm">
              <caption className="sr-only">Usuarios de la aplicación</caption>
              <thead className="text-left text-xs uppercase text-slate-600">
                <tr>
                  <th scope="col" className="py-1.5 pr-3">
                    Nombre
                  </th>
                  <th scope="col" className="py-1.5 pr-3">
                    Email
                  </th>
                  <th scope="col" className="py-1.5 pr-3">
                    Empresa
                  </th>
                  <th scope="col" className="py-1.5">
                    Rol
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.data.map((u) => (
                  <tr key={u.id} className="border-t border-slate-100">
                    <td className="py-1.5 pr-3 font-medium">{u.name}</td>
                    <td className="py-1.5 pr-3">{u.email}</td>
                    <td className="py-1.5 pr-3">{u.company ?? '—'}</td>
                    <td className="py-1.5">
                      <Badge tone={u.role === 'ADMIN' ? 'violet' : 'slate'}>{ROLE_LABELS[u.role]}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Dar de alta un usuario" titleId="settings-new-user">
        <form
          onSubmit={handleSubmit((values) => {
            setFormErrors([])
            setCreated(null)
            create.mutate(values)
          })}
          noValidate
          className="grid max-w-2xl gap-4 sm:grid-cols-2"
          aria-label="Alta de usuario"
        >
          {created && (
            <div className="sm:col-span-2">
              <Alert tone="success">{created}</Alert>
            </div>
          )}
          <div className="sm:col-span-2">
            <FormErrors messages={formErrors} />
          </div>
          <TextField label="Nombre" autoComplete="off" error={errors.name?.message} {...register('name')} />
          <TextField
            label="Email"
            type="email"
            autoComplete="off"
            error={errors.email?.message}
            {...register('email')}
          />
          <TextField
            label="Contraseña inicial"
            type="password"
            autoComplete="new-password"
            hint="Entre 12 y 128 caracteres."
            error={errors.password?.message}
            {...register('password')}
          />
          <TextField label="Empresa" autoComplete="off" error={errors.company?.message} {...register('company')} />
          <SelectField label="Zona horaria" error={errors.timezone?.message} {...register('timezone')}>
            {zones.map((zone) => (
              <option key={zone} value={zone}>
                {zone}
              </option>
            ))}
          </SelectField>
          <SelectField label="Rol" error={errors.role?.message} {...register('role')}>
            <option value="USER">{ROLE_LABELS.USER}</option>
            <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
          </SelectField>
          <div className="sm:col-span-2">
            <Button type="submit" busy={create.isPending}>
              Crear usuario
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
