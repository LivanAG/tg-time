import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { CalendarDayDto, MonthRowDto, PeriodSummaryDto } from '../api/types'
import { authResponse, octoberSummary, period } from '../test/fixtures'
import { json, mockApi, noContent } from '../test/fetchMock'
import { renderApp } from '../test/renderApp'

function octoberCalendar(): CalendarDayDto[] {
  return octoberSummary().days.map((d) => ({
    date: d.date,
    dayType: d.dayType === 'FUERA_DE_PERIODO' ? 'LABORABLE' : d.dayType,
    intensive: d.intensive,
    dayMinutes: d.dayMinutes,
    holidayName: d.holidayName,
    absence: d.absence,
    hasWorkday: d.workday !== null,
  }))
}

function row(
  month: string,
  status: MonthRowDto['status'],
  worked: number,
  theoretical: number,
  cumulative: number,
): MonthRowDto {
  return {
    month,
    status,
    workingDays: 21,
    normalDays: 21,
    intensiveDays: 0,
    calendarMinutes: 10080,
    vacationDays: 0,
    vacationMinutes: 0,
    theoreticalMinutes: theoretical,
    workedMinutes: worked,
    differenceMinutes: worked - theoretical,
    bridgeMinutes: 0,
    cumulativeBalanceMinutes: cumulative,
  }
}

function periodSummary(): PeriodSummaryDto {
  return {
    periodId: period.id,
    name: '2026-2027',
    startDate: '2026-05-26',
    endDate: '2027-05-25',
    today: '2026-10-07',
    workingDays: 248,
    normalDays: 181,
    intensiveDays: 67,
    calendarMinutes: 115020,
    agreementMinutes: 105600,
    marginMinutes: 9420,
    vacations: {
      totalDays: 23,
      plannedDays: 13,
      plannedMinutes: 5460,
      takenDays: 13,
      takenMinutes: 5460,
      pendingPlannedDays: 0,
      pendingPlannedMinutes: 0,
      remainingDays: 10,
      remainingMinutes: 4800,
      unplannedDays: 10,
      unplannedMinutes: 4800,
      valueMinutes: 10260,
    },
    hoursToRecoverMinutes: 840,
    remainingMarginMinutes: 3960,
    workedMinutes: 37674,
    theoreticalRemainingMinutes: 72480,
    projectionMinutes: -246,
    openingBalanceMinutes: 0,
    balanceToDateMinutes: 594,
    bridgeMinutes: 0,
    months: [
      row('2026-09', 'PAST', 10140, 10080, 654),
      row('2026-10', 'CURRENT', 1984, 9600, -6962),
      row('2026-11', 'FUTURE', 0, 9600, -16562),
    ],
    warnings: [],
  }
}

describe('Calendario', () => {
  it('colorea los días por tipo y permite marcar una ausencia', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      [`GET /api/periods/${period.id}/calendar`]: octoberCalendar(),
      [`GET /api/summary/period/${period.id}`]: periodSummary(),
      'PUT /api/absences/2026-10-13': (req) => json({ date: '2026-10-13', ...(req.body as object) }),
    })
    renderApp('/calendario')

    const october = await screen.findByRole('region', { name: 'Octubre de 2026' })
    const holiday = within(october).getByRole('img', {
      name: /Lunes, 12 de octubre de 2026 · Festivo: Fiesta Nacional de España/,
    })
    expect(holiday).toHaveAttribute('title', expect.stringContaining('Fiesta Nacional de España'))
    expect(holiday).toHaveClass('bg-rose-200')
    expect(within(october).getByRole('button', { name: /Viernes, 9 de octubre.*Vacaciones/ })).toHaveClass(
      'bg-emerald-500',
    )
    expect(within(october).getByRole('button', { name: /Jueves, 1 de octubre.*con fichaje/ })).toBeInTheDocument()
    expect(within(october).getByRole('img', { name: /Sábado, 3 de octubre.*Fin de semana/ })).toHaveClass(
      'bg-slate-100',
    )

    // Contador de vacaciones.
    const counter = await screen.findByRole('region', { name: 'Vacaciones 2026-2027' })
    expect(within(counter).getByText('Disfrutadas').nextElementSibling).toHaveTextContent('13 días')
    expect(within(counter).getByText('Restantes').nextElementSibling).toHaveTextContent('10 días')
    expect(screen.getByRole('region', { name: 'Leyenda' })).toHaveTextContent('Intensiva 7 h')

    fireEvent.click(within(october).getByRole('button', { name: /Martes, 13 de octubre/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Martes, 13 de octubre de 2026' })
    fireEvent.click(within(dialog).getByRole('radio', { name: /Puente recuperable/ }))
    fireEvent.click(within(dialog).getByLabelText('Medio día'))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Marcar ausencia' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(callsTo('PUT', '/api/absences/2026-10-13')[0].body).toEqual({ type: 'PUENTE', halfDay: true, note: null })
    // Se recalcula el calendario.
    await waitFor(() => expect(callsTo('GET', `/api/periods/${period.id}/calendar`).length).toBeGreaterThan(1))
  })

  it('quita una ausencia existente', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      [`GET /api/periods/${period.id}/calendar`]: octoberCalendar(),
      [`GET /api/summary/period/${period.id}`]: periodSummary(),
      'DELETE /api/absences/2026-10-09': () => noContent(),
    })
    renderApp('/calendario')

    fireEvent.click(await screen.findByRole('button', { name: /Viernes, 9 de octubre/ }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('radio', { name: /Vacaciones/ })).toBeChecked()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Quitar ausencia' }))

    await waitFor(() => expect(callsTo('DELETE', '/api/absences/2026-10-09')).toHaveLength(1))
  })
})

describe('Resumen anual', () => {
  it('pinta la tabla de meses con signos y atenúa los meses futuros, y el bloque de margen y proyección', async () => {
    mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      [`GET /api/summary/period/${period.id}`]: periodSummary(),
    })
    renderApp('/resumen')

    const table = await screen.findByRole('table', { name: 'Horas por mes del periodo' })
    const rows = within(table).getAllByRole('row')
    const september = rows.find((r) => r.getAttribute('data-status') === 'PAST') as HTMLElement
    const november = rows.find((r) => r.getAttribute('data-status') === 'FUTURE') as HTMLElement
    expect(september).toHaveTextContent('Sep 2026')
    expect(within(september).getByText('+1:00')).toHaveClass('text-emerald-700')
    expect(within(september).getByText('+10:54')).toBeInTheDocument()
    expect(november).toHaveClass('text-slate-400')
    expect(november).toHaveTextContent('-276:02')

    const margin = screen.getByRole('region', { name: 'Margen sobre el convenio' })
    expect(within(margin).getByText('Horas calendario').nextElementSibling).toHaveTextContent('1917:00')
    expect(within(margin).getByText('Convenio').nextElementSibling).toHaveTextContent('1760:00')
    expect(within(margin).getByText('+157:00')).toBeInTheDocument()
    expect(within(margin).getByText('Horas a recuperar').nextElementSibling).toHaveTextContent('14:00')
    expect(within(margin).getByText('+66:00')).toBeInTheDocument()

    const hours = screen.getByRole('region', { name: 'Vacaciones y horas' })
    expect(within(hours).getByText('-4:06')).toHaveClass('text-red-600')
    expect(within(hours).getByText('Saldo hasta hoy').nextElementSibling).toHaveTextContent('+9:54')
  })

  it('exporta el periodo seleccionado a Excel con su nombre de fichero', async () => {
    // jsdom no implementa las URL de blobs.
    const createObjectURL = vi.fn(() => 'blob:periodo')
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const api = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      [`GET /api/summary/period/${period.id}`]: periodSummary(),
      'GET /api/export/xlsx/period': () =>
        new Response('xlsx', { headers: { 'Content-Disposition': 'attachment; filename="horas-2026-2027.xlsx"' } }),
    })
    renderApp('/resumen')

    fireEvent.click(await screen.findByRole('button', { name: 'Exportar a Excel' }))

    await waitFor(() => expect(api.callsTo('GET', '/api/export/xlsx/period')).toHaveLength(1))
    expect(api.callsTo('GET', '/api/export/xlsx/period')[0].url.searchParams.get('periodId')).toBe(period.id)
    await waitFor(() => expect(click).toHaveBeenCalled())
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('horas-2026-2027.xlsx')
    expect(createObjectURL).toHaveBeenCalledOnce()
    click.mockRestore()
  })
})
