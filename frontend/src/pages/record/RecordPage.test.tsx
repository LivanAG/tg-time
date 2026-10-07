import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { authResponse, octoberSummary, period } from '../../test/fixtures'
import { json, mockApi, problem } from '../../test/fetchMock'
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
    expect(screen.getByText('Octubre de 2026')).toBeInTheDocument()

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
    expect(within(close).getByText('Faltan')).toBeInTheDocument()
    expect(within(close).getByText('-126:56')).toHaveClass('text-red-600')
    expect(within(close).getByText('+8:50')).toBeInTheDocument()
    expect(within(close).getByText('-118:06')).toHaveClass('text-red-600')
    expect(within(close).getByText('33,3 %')).toBeInTheDocument()
    expect(within(close).getByText('Días en casa').nextElementSibling).toHaveTextContent('2')
  })

  it('despliega los avisos de un día', async () => {
    mockApi(routes())
    renderApp('/registro/2026-10')

    fireEvent.click(await screen.findByRole('button', { name: '1 aviso del jue 1' }))

    expect(screen.getByText('⚠ La comida dura 20 min: se descuenta el mínimo de 30 min')).toBeInTheDocument()
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
      remoteMinutes: null,
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
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    const firstDay = within(week).getByRole('button', { name: /Jue 1/ })
    expect(firstDay).toHaveTextContent('07:25 – 17:59')
    expect(firstDay).toHaveTextContent('10:04')
    expect(firstDay).toHaveTextContent('red. 10:00')
    // Navegación inferior en lugar de la lateral.
    expect(screen.getByRole('navigation', { name: 'Principal' })).toHaveClass('fixed')
  })

  it('avisa si ningún periodo incluye el mes', async () => {
    mockApi(routes())
    renderApp('/registro/2025-01')

    expect(await screen.findByText('Ningún periodo incluye este mes')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mes siguiente' })).toHaveAttribute('href', '/registro/2025-02')
  })

  it('una ruta sin mes válido redirige al mes actual', async () => {
    mockApi(routes())
    renderApp('/registro/otra-cosa')

    expect(await screen.findByRole('heading', { name: 'Registro' })).toBeInTheDocument()
  })
})
