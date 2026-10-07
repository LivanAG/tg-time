import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { getAccessToken } from '../../api/client'
import { authResponse, period, user } from '../../test/fixtures'
import { json, mockApi, noContent, problem } from '../../test/fetchMock'
import { renderApp } from '../../test/renderApp'

describe('Ajustes', () => {
  it('cambiar la contraseña cierra la sesión y pide un nuevo login', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'PUT /api/me/password': () => noContent(),
      'POST /api/auth/logout': () => noContent(),
      'GET /actuator/health': { status: 'UP' },
    })
    renderApp('/ajustes?seccion=contrasena')

    fireEvent.change(await screen.findByLabelText('Contraseña actual'), { target: { value: 'la-actual-de-siempre' } })
    fireEvent.change(screen.getByLabelText('Nueva contraseña'), { target: { value: 'corta' } })
    fireEvent.change(screen.getByLabelText('Repite la nueva contraseña'), { target: { value: 'otra' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar la contraseña' }))
    expect(await screen.findByText('Mínimo 12 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Las contraseñas no coinciden')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Nueva contraseña'), { target: { value: 'una-contraseña-nueva-larga' } })
    fireEvent.change(screen.getByLabelText('Repite la nueva contraseña'), {
      target: { value: 'una-contraseña-nueva-larga' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar la contraseña' }))

    expect(await screen.findByText('Contraseña cambiada. Inicia sesión con la nueva contraseña.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(callsTo('PUT', '/api/me/password')[0].body).toEqual({
      currentPassword: 'la-actual-de-siempre',
      newPassword: 'una-contraseña-nueva-larga',
    })
    await waitFor(() => expect(callsTo('POST', '/api/auth/logout')).toHaveLength(1))
    expect(getAccessToken()).toBeNull()
  })

  it('muestra junto al campo el error de contraseña común que devuelve el backend', async () => {
    mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'PUT /api/me/password': () =>
        problem(400, 'Datos no válidos', [{ field: 'newPassword', message: 'Esa contraseña es demasiado común' }]),
    })
    renderApp('/ajustes?seccion=contrasena')

    fireEvent.change(await screen.findByLabelText('Contraseña actual'), { target: { value: 'la-actual-de-siempre' } })
    fireEvent.change(screen.getByLabelText('Nueva contraseña'), { target: { value: 'password1234' } })
    fireEvent.change(screen.getByLabelText('Repite la nueva contraseña'), { target: { value: 'password1234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar la contraseña' }))

    expect(await screen.findByText('Esa contraseña es demasiado común')).toBeInTheDocument()
    expect(screen.getByLabelText('Nueva contraseña')).toHaveAttribute('aria-invalid', 'true')
  })

  it('guarda el perfil con PUT /api/me', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'PUT /api/me': (req) => json({ ...user, ...(req.body as object) }),
    })
    renderApp('/ajustes')

    fireEvent.change(await screen.findByLabelText('Nombre'), { target: { value: 'Livan A.' } })
    fireEvent.change(screen.getByLabelText('Empresa'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar perfil' }))

    expect(await screen.findByText('Perfil guardado.')).toBeInTheDocument()
    expect(callsTo('PUT', '/api/me')[0].body).toEqual({ name: 'Livan A.', company: null, timezone: 'Europe/Madrid' })
  })

  it('edita un periodo: PUT con version y después los rangos de intensiva', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      [`PUT /api/periods/${period.id}`]: (req) => json({ ...period, ...(req.body as object), version: 1 }),
      [`PUT /api/periods/${period.id}/intensive-ranges`]: (req) => json(req.body),
    })
    renderApp('/ajustes?seccion=periodos')

    fireEvent.click(await screen.findByRole('button', { name: 'Editar el periodo 2026-2027' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar 2026-2027' })
    expect(within(dialog).getByLabelText('Jornada normal (h:mm)')).toHaveValue('8:00')
    expect(within(dialog).getByLabelText('Tolerancia de desayuno (min)')).toHaveValue('20')
    expect(within(dialog).queryByLabelText('Precargar los festivos de Madrid')).not.toBeInTheDocument()

    fireEvent.change(within(dialog).getByLabelText('Saldo inicial (h:mm)'), { target: { value: '-1:30' } })
    fireEvent.change(within(dialog).getByLabelText('Fin del rango 1'), { target: { value: '2026-09-30' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '+ Añadir rango' }))
    fireEvent.change(within(dialog).getByLabelText('Inicio del rango 2'), { target: { value: '2026-09-20' } })
    fireEvent.change(within(dialog).getByLabelText('Fin del rango 2'), { target: { value: '2026-10-05' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    // Rangos solapados: error antes de llamar a la API.
    expect(await within(dialog).findByText('Los rangos no pueden solaparse')).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Quitar el rango 2' }))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Periodo 2026-2027 guardado.')).toBeInTheDocument()
    const update = callsTo('PUT', `/api/periods/${period.id}`)[0].body as Record<string, unknown>
    expect(update).toMatchObject({ openingBalanceMin: -90, version: 0, agreementMinutes: 105600 })
    expect(update).not.toHaveProperty('preloadHolidays')
    expect(callsTo('PUT', `/api/periods/${period.id}/intensive-ranges`)[0].body).toEqual([
      { startDate: '2026-06-15', endDate: '2026-09-30' },
    ])
  })

  it('un periodo nuevo empieza el día después del último', async () => {
    mockApi({ 'POST /api/auth/refresh': json(authResponse()), 'GET /api/periods': [period] })
    renderApp('/ajustes?seccion=periodos')

    await screen.findByRole('button', { name: 'Editar el periodo 2026-2027' })
    fireEvent.click(screen.getByRole('button', { name: 'Nuevo periodo' }))
    const dialog = await screen.findByRole('dialog', { name: 'Nuevo periodo' })
    expect(within(dialog).getByLabelText('Nombre')).toHaveValue('2027-2028')
    expect(within(dialog).getByLabelText('Fecha de inicio')).toHaveValue('2027-05-26')
    expect(within(dialog).getByLabelText('Fecha de fin')).toHaveValue('2028-05-25')
    expect(within(dialog).getByLabelText('Inicio del rango 1')).toHaveValue('2027-06-15')
  })

  it('añade y borra festivos del periodo', async () => {
    const holidays = [{ id: 'h1', date: '2026-10-12', name: 'Fiesta Nacional de España', scope: 'NACIONAL' }]
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      [`GET /api/periods/${period.id}/holidays`]: () => json(holidays),
      [`POST /api/periods/${period.id}/holidays`]: (req) => json({ id: 'h2', ...(req.body as object) }, 201),
      [`DELETE /api/periods/${period.id}/holidays/h1`]: () => noContent(),
    })
    renderApp('/ajustes?seccion=festivos')

    expect(await screen.findByText('Fiesta Nacional de España')).toBeInTheDocument()
    const form = screen.getByRole('form', { name: 'Añadir festivo' })
    fireEvent.change(within(form).getByLabelText('Fecha'), { target: { value: '2026-11-09' } })
    fireEvent.change(within(form).getByLabelText('Nombre'), { target: { value: 'Almudena' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Añadir' }))

    expect(await screen.findByText('Festivo «Almudena» añadido.')).toBeInTheDocument()
    expect(callsTo('POST', `/api/periods/${period.id}/holidays`)[0].body).toEqual({
      date: '2026-11-09',
      name: 'Almudena',
      scope: 'LOCAL',
    })

    fireEvent.click(screen.getByRole('button', { name: 'Borrar el festivo Fiesta Nacional de España' }))
    await waitFor(() => expect(callsTo('DELETE', `/api/periods/${period.id}/holidays/h1`)).toHaveLength(1))
  })

  it('solo el ADMIN ve y da de alta usuarios', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/admin/users': [user],
      'POST /api/admin/users': () => problem(409, 'El email ya existe'),
    })
    renderApp('/ajustes?seccion=usuarios')

    expect(await screen.findByRole('table', { name: 'Usuarios de la aplicación' })).toHaveTextContent(
      'livan@example.com',
    )
    const form = screen.getByRole('form', { name: 'Alta de usuario' })
    fireEvent.change(within(form).getByLabelText('Nombre'), { target: { value: 'Ana' } })
    fireEvent.change(within(form).getByLabelText('Email'), { target: { value: 'livan@example.com' } })
    fireEvent.change(within(form).getByLabelText('Contraseña inicial'), { target: { value: 'una-contraseña-segura' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Crear usuario' }))

    expect(await within(form).findByText('Ya existe un usuario con ese email')).toBeInTheDocument()
    expect(callsTo('POST', '/api/admin/users')[0].body).toEqual({
      name: 'Ana',
      email: 'livan@example.com',
      password: 'una-contraseña-segura',
      company: 'IZERTIS',
      timezone: 'Europe/Madrid',
      role: 'USER',
    })
  })

  it('un USER no ve la sección de usuarios', async () => {
    mockApi({ 'POST /api/auth/refresh': json(authResponse('t', { role: 'USER' })) })
    renderApp('/ajustes?seccion=usuarios')

    expect(await screen.findByRole('heading', { name: 'Perfil' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument()
  })
})
