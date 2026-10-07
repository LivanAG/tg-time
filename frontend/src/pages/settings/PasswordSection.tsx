import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { meApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { Button, Card, FormErrors, TextField } from '../../components/ui'
import { applyServerErrors } from '../../lib/formErrors'
import { passwordSchema } from '../../lib/password'

function changePasswordSchema(email: string) {
  return z
    .object({
      currentPassword: z.string().min(1, 'Indica tu contraseña actual'),
      newPassword: passwordSchema(() => email),
      confirmPassword: z.string(),
    })
    .refine((v) => v.newPassword === v.confirmPassword, {
      path: ['confirmPassword'],
      message: 'Las contraseñas no coinciden',
    })
}

type PasswordValues = z.infer<ReturnType<typeof changePasswordSchema>>

export function PasswordSection() {
  const { user, logout } = useAuth()
  const [formErrors, setFormErrors] = useState<string[]>([])
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<PasswordValues>({
    resolver: zodResolver(changePasswordSchema(user?.email ?? '')),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const change = useMutation({
    mutationFn: (values: PasswordValues) =>
      meApi.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
    // El backend revoca todas las sesiones: hay que volver a iniciar sesión.
    onSuccess: () => logout('Contraseña cambiada. Inicia sesión con la nueva contraseña.'),
    onError: (error) => setFormErrors(applyServerErrors(error, setError, ['currentPassword', 'newPassword'])),
  })

  return (
    <Card title="Cambiar la contraseña" titleId="settings-password">
      <form
        onSubmit={handleSubmit((values) => {
          setFormErrors([])
          change.mutate(values)
        })}
        noValidate
        className="max-w-lg space-y-4"
        aria-label="Cambiar la contraseña"
      >
        <p className="text-sm text-slate-600">
          Al cambiarla se cierran todas tus sesiones (también en otros dispositivos) y tendrás que volver a entrar.
        </p>
        <FormErrors messages={formErrors} />
        {/* Campo oculto para que los gestores de contraseñas asocien el cambio a la cuenta. */}
        <input type="email" autoComplete="username" value={user?.email ?? ''} readOnly hidden />
        <TextField
          label="Contraseña actual"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <TextField
          label="Nueva contraseña"
          type="password"
          autoComplete="new-password"
          hint="Entre 12 y 128 caracteres, que no sea una contraseña común ni tu email."
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <TextField
          label="Repite la nueva contraseña"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <Button type="submit" busy={change.isPending}>
          Cambiar la contraseña
        </Button>
      </form>
    </Card>
  )
}
