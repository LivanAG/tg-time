import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { authResponse, dashboard, dashboardWithoutPeriod, period, referenceDay } from '../test/fixtures'
import { json, mockApi, noContent, problem } from '../test/fetchMock'
import { renderApp } from '../test/renderApp'

describe('Inicio', () => {
  it('con un periodo que aún no ha empezado lo avisa y ofrece cambiar al que incluye hoy', async () => {
    const next = {
      ...period,
      id: '7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f',
      name: '2027-2028',
      startDate: '2027-05-26',
      endDate: '2028-05-25',
    }
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [
        { ...next, selected: true },
        { ...period, selected: false },
      ],
      'GET /api/summary/dashboard': dashboard({
        period: { id: next.id, name: next.name, startDate: next.startDate, endDate: next.endDate },
        balanceToDateMinutes: 0,
        currentMonth: { ...dashboard().currentMonth!, month: '2027-05' },
        todayIsWorkingDay: false,
        todayWorkday: null,
      }),
      [`PUT /api/periods/${period.id}/select`]: noContent(),
    })

    renderApp('/')

    expect(await screen.findByText('Estás trabajando con el periodo 2027-2028')).toBeInTheDocument()
    expect(screen.getByText('Saldo al empezar el periodo')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Mayo de 2027' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Fichar hoy' })).not.toBeInTheDocument()

    fireEvent.click(await screen.findByRole('button', { name: 'Cambiar a 2026-2027 para fichar hoy' }))
    await waitFor(() => expect(callsTo('PUT', `/api/periods/${period.id}/select`)).toHaveLength(1))
  })

  it('con periodo muestra el saldo con signo y color, el mes, las vacaciones y el teletrabajo', async () => {
    mockApi({ 'POST /api/auth/refresh': json(authResponse()), 'GET /api/summary/dashboard': dashboard() })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Hola, Livan' })).toBeInTheDocument()
    const balance = screen.getByText('+9:54')
    expect(balance).toHaveClass('text-emerald-700')

    const month = screen.getByRole('region', { name: /Mes actual: octubre de 2026/ })
    expect(within(month).getByText('168:00')).toBeInTheDocument()
    // Hechas del mes y hechas hasta hoy coinciden (2406 min).
    expect(within(month).getAllByText('40:06')).toHaveLength(2)
    expect(within(month).getByText('40:00')).toBeInTheDocument()
    expect(within(month).getByText('-127:54')).toHaveClass('text-red-600')
    expect(within(month).getByText('+0:06')).toHaveClass('text-emerald-700')

    const vacations = screen.getByRole('region', { name: 'Vacaciones' })
    expect(vacations).toHaveTextContent('10de 23 días restantes')
    expect(vacations).toHaveTextContent('13 días')

    const remote = screen.getByRole('region', { name: 'Teletrabajo del mes' })
    expect(remote).toHaveTextContent('39,7 %')
    expect(remote).toHaveTextContent('máx. 50 %')
    expect(within(remote).getAllByRole('meter')).toHaveLength(1)
    expect(remote).toHaveTextContent('Días en casa o mixtos2')
    expect(remote).not.toHaveTextContent('máx. 8')

    const projection = screen.getByRole('region', { name: 'Horas a recuperar y proyección' })
    expect(within(projection).getByText('14:00')).toBeInTheDocument()
    expect(within(projection).getByText('-4:06')).toHaveClass('text-red-600')

    expect(screen.getByRole('region', { name: 'Avisos del mes' })).toHaveTextContent('Sin avisos este mes.')
  })

  it('"Fichar hoy" abre el editor del día de hoy', async () => {
    mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/summary/dashboard': dashboard(),
      'GET /api/periods': [period],
      'GET /api/workdays/2026-10-07': () => problem(404, 'No existe'),
      'GET /api/absences/2026-10-07': () => problem(404, 'No existe'),
    })

    renderApp('/')

    fireEvent.click(await screen.findByRole('button', { name: 'Fichar hoy' }))

    const dialog = await screen.findByRole('dialog', { name: 'Miércoles, 7 de octubre de 2026' })
    expect(within(dialog).getByText('Nuevo fichaje')).toBeInTheDocument()
    expect(await within(dialog).findByLabelText('Entrada')).toBeInTheDocument()
    expect(dialog).toHaveFocus()
  })

  it('con el día ya fichado muestra el horario y permite editarlo', async () => {
    mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/summary/dashboard': dashboard({ todayWorkday: { ...referenceDay, date: '2026-10-07' } }),
    })

    renderApp('/')

    expect(await screen.findByText('07:25 – 17:59')).toBeInTheDocument()
    expect(screen.getByText('10:04')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar el fichaje de hoy' })).toBeInTheDocument()
  })

  it('sin periodo muestra el asistente de bienvenida con enlaces a Ajustes', async () => {
    mockApi({ 'POST /api/auth/refresh': json(authResponse()), 'GET /api/summary/dashboard': dashboardWithoutPeriod() })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Primeros pasos' })).toBeInTheDocument()
    expect(screen.queryByText('Saldo acumulado hasta hoy')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Crear el periodo' })).toHaveAttribute(
      'href',
      '/ajustes?seccion=periodos&nuevo=1',
    )
    expect(screen.getByRole('link', { name: 'Importar el Excel' })).toHaveAttribute('href', '/ajustes?seccion=importar')
    expect(screen.getByText(/1760 h de convenio, 23 días/)).toBeInTheDocument()
  })

  it('el asistente lleva al alta del periodo con los valores del Excel ya rellenos', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/summary/dashboard': dashboardWithoutPeriod(),
      'GET /api/periods': [],
      'POST /api/periods': (req) => json({ ...period, ...(req.body as object), id: period.id, version: 0 }, 201),
    })

    renderApp('/')
    fireEvent.click(await screen.findByRole('link', { name: 'Crear el periodo' }))

    const dialog = await screen.findByRole('dialog', { name: 'Nuevo periodo' })
    expect(within(dialog).getByLabelText('Nombre')).toHaveValue('2026-2027')
    expect(within(dialog).getByLabelText('Fecha de inicio')).toHaveValue('2026-05-26')
    expect(within(dialog).getByLabelText('Fecha de fin')).toHaveValue('2027-05-25')
    expect(within(dialog).getByLabelText('Horas de convenio (h:mm)')).toHaveValue('1760:00')
    expect(within(dialog).getByLabelText('Inicio del rango 1')).toHaveValue('2026-06-15')
    expect(within(dialog).getByLabelText('Precargar los festivos de Madrid')).toBeChecked()

    fireEvent.click(within(dialog).getByRole('button', { name: 'Crear periodo' }))

    expect(await screen.findByText('Periodo 2026-2027 creado y en uso.')).toBeInTheDocument()
    expect(callsTo('POST', '/api/periods')[0].body).toEqual({
      name: '2026-2027',
      startDate: '2026-05-26',
      endDate: '2027-05-25',
      agreementMinutes: 105600,
      vacationDays: 23,
      normalDayMinutes: 480,
      intensiveDayMinutes: 420,
      breakfastToleranceMin: 20,
      minLunchMin: 30,
      roundingStepMin: 15,
      maxRemotePct: 50,
      openingBalanceMin: 0,
      intensiveRanges: [{ startDate: '2026-06-15', endDate: '2026-09-15' }],
      preloadHolidays: true,
    })
  })
})
