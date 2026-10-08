import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { PeriodDto } from '../../api/types'
import { referenceMonth } from '../../hooks/usePeriods'
import { capitalize, formatMonthLong } from '../../lib/dates'
import { authResponse, octoberSummary, period } from '../../test/fixtures'
import { json, mockApi, noContent, problem } from '../../test/fetchMock'
import { renderApp } from '../../test/renderApp'

const MONTH_URL = 'GET /api/summary/month'

function routes(extra: Record<string, unknown> = {}) {
  return {
    'POST /api/auth/refresh': json(authResponse()),
    [MONTH_URL]: (req: { url: URL }) =>
      req.url.searchParams.get('year') === '2026' && req.url.searchParams.get('month') === '10'
        ? json(octoberSummary())
        : problem(404, 'Ningún periodo incluye el mes'),
    'GET /api/periods': [period],
    ...extra,
  }
}

describe('Registro mensual', () => {
  it('pinta las semanas con subtotales, los tipos de día y el cierre del mes', async () => {
    mockApi(routes())
    renderApp('/registro/2026-10')

    expect(await screen.findByRole('heading', { name: 'Registro' })).toBeInTheDocument()
    expect(screen.getByText('Octubre de 2026 · periodo 2026-2027')).toBeInTheDocument()

    const table = await screen.findByRole('table', { name: 'Registro diario del mes por semanas' })
    const subtotals = within(table).getAllByRole('row', { name: /Subtotal de la semana/ })
    expect(subtotals).toHaveLength(5)
    // Semana del 1 al 4: 604 + 420 = 17:04 trabajadas, 17:00 redondeadas, 16:00 teóricas.
    expect(subtotals[0]).toHaveTextContent('Semana 1 oct – 4 oct')
    expect(subtotals[0]).toHaveTextContent('teóricas 16:00')
    expect(subtotals[0]).toHaveTextContent('17:04')
    expect(subtotals[0]).toHaveTextContent('17:00')
    expect(within(subtotals[0]).getByText('+1:04')).toHaveClass('text-emerald-700')

    // Día de referencia: entrada, pausas, salida, total y redondeado.
    expect(screen.getByLabelText('Entrada del jue 1')).toHaveValue('07:25')
    expect(screen.getByLabelText('Salida del jue 1')).toHaveValue('17:59')
    const firstRow = screen.getByLabelText('Entrada del jue 1').closest('tr') as HTMLElement
    expect(firstRow).toHaveTextContent('D 12:43–13:02 · C 15:02–15:32')
    expect(firstRow).toHaveTextContent('10:04')
    expect(firstRow).toHaveTextContent('10:00')
    expect(within(firstRow).getByRole('button', { name: '1 aviso del jue 1' })).toBeInTheDocument()
    expect(screen.getByLabelText('Ubicación del mar 6')).toHaveValue('MIXTO')

    // Festivo, vacaciones y fines de semana diferenciados.
    expect(within(table).getByText('Festivo: Fiesta Nacional de España').closest('tr')).toHaveClass('bg-rose-50')
    expect(within(table).getByText('Vacaciones').closest('tr')).toHaveClass('bg-emerald-50')
    expect(within(table).getAllByText('Fin de semana')).toHaveLength(9)

    // Cierre del mes.
    const close = screen.getByRole('region', { name: 'Cierre del mes' })
    expect(within(close).getByText('160:00')).toBeInTheDocument()
    expect(within(close).getAllByText('33:04').length).toBeGreaterThan(0)
    // Tabla de horas: mes completo y hasta hoy, con la diferencia con signo y color.
    const difference = within(close).getByRole('row', { name: /Diferencia/ })
    expect(within(difference).getAllByText('-126:56')[0]).toHaveClass('text-red-600')
    expect(within(close).getAllByText('+8:50').length).toBeGreaterThan(0)
    // Saldo como una cuenta: apertura + diferencia − puentes = cierre.
    expect(within(close).getByText('= Saldo de cierre').nextElementSibling).toHaveTextContent('-118:06')
    expect(within(close).getByText('-118:06')).toHaveClass('text-red-600')
    expect(within(close).getByText('33,3 %')).toBeInTheDocument()
    expect(close).toHaveTextContent('2 días en casa o mixto')
  })

  it('despliega los avisos de un día', async () => {
    mockApi(routes())
    renderApp('/registro/2026-10')

    fireEvent.click(await screen.findByRole('button', { name: '1 aviso del jue 1' }))

    expect(screen.getByText('⚠ La comida dura 20 min: se descuenta el mínimo de 30 min')).toBeInTheDocument()
  })

  it('un día mixto se ve y se edita en dos líneas: oficina y casa', async () => {
    const saved = vi.fn()
    mockApi(
      routes({
        'PUT /api/workdays/2026-10-06': (req: { body: unknown }) => {
          saved(req.body)
          return json({ date: '2026-10-06', version: 1 })
        },
      }),
    )
    renderApp('/registro/2026-10')

    const officeStart = await screen.findByLabelText('Entrada en la oficina del mar 6')
    expect(officeStart).toHaveValue('08:00')
    expect(screen.getByLabelText('Salida de la oficina del mar 6')).toHaveValue('12:00')
    expect(screen.getByLabelText('Entrada en casa del mar 6')).toHaveValue('12:00')
    expect(screen.getByLabelText('Salida de casa del mar 6')).toHaveValue('16:30')
    const row = officeStart.closest('tr') as HTMLElement
    expect(row).toHaveTextContent('oficina 4:00 · casa 4:00')

    // Salir de la oficina a las 11:30: la media hora hasta empezar en casa no cuenta.
    fireEvent.change(screen.getByLabelText('Salida de la oficina del mar 6'), { target: { value: '11:30' } })
    expect(row).toHaveTextContent('oficina 3:30 · casa 4:00')
    fireEvent.keyDown(screen.getByLabelText('Salida de la oficina del mar 6'), { key: 'Enter' })

    await waitFor(() => expect(saved).toHaveBeenCalledTimes(1))
    expect(saved).toHaveBeenLastCalledWith(
      expect.objectContaining({
        startTime: '08:00',
        endTime: '16:30',
        location: 'MIXTO',
        officeStart: '08:00',
        officeEnd: '11:30',
        homeStart: '12:00',
        homeEnd: '16:30',
      }),
    )
  })

  it('guarda la fila al salir de ella con PUT y version', async () => {
    const saved = vi.fn()
    const { callsTo } = mockApi(
      routes({
        'PUT /api/workdays/2026-10-07': (req: { body: unknown }) => {
          saved(req.body)
          return json({ date: '2026-10-07', version: 0 })
        },
        'PUT /api/workdays/2026-10-01': (req: { body: unknown }) => {
          saved(req.body)
          return json({ date: '2026-10-01', version: 4 })
        },
      }),
    )
    renderApp('/registro/2026-10')

    // Día nuevo: version null.
    const start = await screen.findByLabelText('Entrada del mié 7')
    fireEvent.change(start, { target: { value: '08:00' } })
    fireEvent.change(screen.getByLabelText('Salida del mié 7'), { target: { value: '15:30' } })
    expect(start.closest('tr')).toHaveTextContent('7:30')
    fireEvent.focusOut(screen.getByLabelText('Salida del mié 7'), { relatedTarget: document.body })

    await waitFor(() => expect(saved).toHaveBeenCalledTimes(1))
    expect(saved).toHaveBeenLastCalledWith({
      startTime: '08:00',
      endTime: '15:30',
      breaks: [],
      location: 'OFICINA',
      officeStart: null,
      officeEnd: null,
      homeStart: null,
      homeEnd: null,
      notes: null,
      version: null,
    })

    // Salir otra vez de la fila sin cambios no reenvía; un cambio posterior usa la versión devuelta.
    fireEvent.focusOut(screen.getByLabelText('Salida del mié 7'), { relatedTarget: document.body })
    fireEvent.change(screen.getByLabelText('Salida del mié 7'), { target: { value: '16:00' } })
    fireEvent.focusOut(screen.getByLabelText('Salida del mié 7'), { relatedTarget: document.body })
    await waitFor(() => expect(saved).toHaveBeenCalledTimes(2))
    expect(saved).toHaveBeenLastCalledWith(expect.objectContaining({ endTime: '16:00', version: 0 }))

    // Día existente: conserva pausas y notas y envía su versión.
    fireEvent.change(screen.getByLabelText('Ubicación del jue 1'), { target: { value: 'CASA' } })
    fireEvent.keyDown(screen.getByLabelText('Ubicación del jue 1'), { key: 'Enter' })

    await waitFor(() => expect(saved).toHaveBeenCalledTimes(3))
    expect(saved).toHaveBeenLastCalledWith(
      expect.objectContaining({
        startTime: '07:25',
        endTime: '17:59',
        breaks: [
          { type: 'DESAYUNO', startTime: '12:43', endTime: '13:02' },
          { type: 'COMIDA', startTime: '15:02', endTime: '15:32' },
        ],
        location: 'CASA',
        version: 3,
      }),
    )
    // Tras guardar se recalcula el mes.
    await waitFor(() => expect(callsTo('GET', '/api/summary/month').length).toBeGreaterThan(1))
  })

  it('valida la fila con las mismas reglas que el backend y no envía nada si hay errores', async () => {
    const { callsTo } = mockApi(routes())
    renderApp('/registro/2026-10')

    const end = await screen.findByLabelText('Salida del jue 1')
    fireEvent.change(end, { target: { value: '07:00' } })
    fireEvent.focusOut(end, { relatedTarget: null })

    expect(await screen.findByRole('alert')).toHaveTextContent('La salida debe ser posterior a la entrada')
    expect(end).toHaveAttribute('aria-invalid', 'true')
    expect(callsTo('PUT', '/api/workdays/2026-10-01')).toHaveLength(0)
  })

  it('el botón Editar abre el editor completo del día', async () => {
    mockApi(
      routes({
        'GET /api/workdays/2026-10-01': json(octoberSummary().days[0].workday),
        'GET /api/absences/2026-10-01': () => problem(404, 'No existe'),
      }),
    )
    renderApp('/registro/2026-10')

    fireEvent.click(await screen.findByRole('button', { name: 'Abrir el editor del jue 1' }))

    const dialog = await screen.findByRole('dialog', { name: 'Jueves, 1 de octubre de 2026' })
    expect(await within(dialog).findByTestId('live-total')).toHaveTextContent('10:04')
  })

  it('en el móvil pinta una tarjeta por día', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: false,
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
    mockApi(routes())
    renderApp('/registro/2026-10')

    const week = await screen.findByRole('region', { name: 'Semana del 1 oct al 4 oct' })
    expect(screen.queryByRole('table', { name: 'Registro diario del mes por semanas' })).not.toBeInTheDocument()
    const firstDay = within(week).getByRole('button', { name: /Jue 1/ })
    expect(firstDay).toHaveTextContent('07:25 – 17:59')
    expect(firstDay).toHaveTextContent('10:04')
    expect(firstDay).toHaveTextContent('red. 10:00')
    // Navegación inferior en lugar de la lateral.
    expect(screen.getByRole('navigation', { name: 'Principal' })).toHaveClass('fixed')
  })

  it('un mes fuera del periodo seleccionado lleva a su primer mes, y no se puede salir del periodo', async () => {
    mockApi(routes())
    renderApp('/registro/2025-01')

    expect(await screen.findByText('Mayo de 2026 · periodo 2026-2027')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Mes anterior' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mes siguiente' })).toHaveAttribute('href', '/registro/2026-06')
  })

  it('sin mes abre el mes de referencia del periodo seleccionado', async () => {
    mockApi(routes())
    renderApp('/registro/otra-cosa')

    const expected = capitalize(formatMonthLong(referenceMonth(period)))
    expect(await screen.findByText(`${expected} · periodo 2026-2027`)).toBeInTheDocument()
  })

  it('el selector de periodo cambia el periodo de todas las pantallas', async () => {
    const next: PeriodDto = {
      ...period,
      id: '7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f',
      name: '2027-2028',
      startDate: '2027-05-26',
      endDate: '2028-05-25',
      selected: false,
    }
    let selectedId = period.id
    const { callsTo } = mockApi(
      routes({
        'GET /api/periods': () => json([next, period].map((p) => ({ ...p, selected: p.id === selectedId }))),
        [`PUT /api/periods/${next.id}/select`]: () => {
          selectedId = next.id
          return noContent()
        },
      }),
    )
    renderApp('/registro/2026-10')
    expect(await screen.findByText('Octubre de 2026 · periodo 2026-2027')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Periodo'), { target: { value: next.id } })

    // Octubre de 2026 queda fuera del 2027-2028: pasa a su primer mes.
    expect(await screen.findByText('Mayo de 2027 · periodo 2027-2028')).toBeInTheDocument()
    expect(callsTo('PUT', `/api/periods/${next.id}/select`)).toHaveLength(1)
    await waitFor(() => {
      const months = callsTo('GET', '/api/summary/month')
      expect(months.some((c) => c.url.searchParams.get('periodId') === next.id)).toBe(true)
    })
  })
})
