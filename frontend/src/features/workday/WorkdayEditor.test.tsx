import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { WorkdayDto } from '../../api/types'
import { period, referenceDay, workday } from '../../test/fixtures'
import { json, mockApi, noContent, problem } from '../../test/fetchMock'
import { renderWithProviders } from '../../test/renderApp'
import { WorkdayEditor } from './WorkdayEditor'

const DATE = '2026-10-07'

function baseRoutes(existing: WorkdayDto | null = null) {
  return {
    'GET /api/periods': [period],
    [`GET /api/workdays/${DATE}`]: existing ? json(existing) : () => problem(404, 'No existe'),
    [`GET /api/absences/${DATE}`]: () => problem(404, 'No existe'),
  }
}

function setTime(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function liveTotal() {
  return screen.getByTestId('live-total')
}

async function openEditor(onClose = vi.fn()) {
  renderWithProviders(<WorkdayEditor date={DATE} onClose={onClose} />)
  await screen.findByLabelText('Entrada')
  return onClose
}

describe('Editor de día', () => {
  it('calcula el total en vivo con las mismas reglas que el backend', async () => {
    mockApi(baseRoutes())
    await openEditor()

    expect(liveTotal()).toHaveTextContent('—')
    setTime('Entrada', '07:25')
    setTime('Salida', '17:59')
    expect(liveTotal()).toHaveTextContent('10:34')

    fireEvent.click(screen.getByRole('button', { name: '+ Desayuno' }))
    setTime('Inicio de la pausa 1', '12:43')
    setTime('Fin de la pausa 1', '13:02')
    fireEvent.click(screen.getByRole('button', { name: '+ Comida' }))
    setTime('Inicio de la pausa 2', '15:02')
    setTime('Fin de la pausa 2', '15:32')
    // Día de referencia de la especificación.
    expect(liveTotal()).toHaveTextContent('10:04')
    expect(screen.getByRole('button', { name: '+ Desayuno' })).toBeDisabled()

    // Desayuno de 25 min: descuenta 5.
    setTime('Fin de la pausa 1', '13:08')
    expect(liveTotal()).toHaveTextContent('9:59')

    // Comida de 26 min: descuenta 30 y avisa.
    setTime('Fin de la pausa 1', '13:02')
    setTime('Fin de la pausa 2', '15:28')
    expect(liveTotal()).toHaveTextContent('10:04')
    expect(screen.getByText(/La comida dura 26 min: se descuenta el mínimo de 30 min/)).toBeInTheDocument()

    // Pausa fuera de la jornada: no hay total y se explica por qué.
    setTime('Inicio de la pausa 2', '06:00')
    expect(liveTotal()).toHaveTextContent('—')
    expect(screen.getByText('La pausa debe estar dentro de la jornada')).toBeInTheDocument()
  })

  it('guarda con PUT (version null al crear) y muestra junto a cada campo los errores 400 de la API', async () => {
    const { callsTo } = mockApi({
      ...baseRoutes(),
      [`PUT /api/workdays/${DATE}`]: () =>
        problem(400, 'Datos no válidos', [
          { field: 'endTime', message: 'La salida no puede ser posterior a las 23:59' },
          { field: 'breaks[0]', message: 'La pausa debe estar dentro de la jornada' },
          { field: 'date', message: 'No hay ningún periodo que incluya la fecha' },
        ]),
    })
    await openEditor()

    setTime('Entrada', '08:00')
    setTime('Salida', '16:00')
    fireEvent.click(screen.getByRole('button', { name: '+ Comida' }))
    setTime('Inicio de la pausa 1', '13:00')
    setTime('Fin de la pausa 1', '13:30')
    fireEvent.click(screen.getByRole('radio', { name: 'Casa' }))
    fireEvent.change(screen.getByLabelText('Notas'), { target: { value: '  Reunión con cliente  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    const endError = await screen.findByText('La salida no puede ser posterior a las 23:59')
    const end = screen.getByLabelText('Salida')
    expect(end).toHaveAttribute('aria-invalid', 'true')
    expect(end).toHaveAttribute('aria-describedby', endError.id)
    expect(screen.getAllByRole('alert').map((a) => a.textContent)).toEqual(
      expect.arrayContaining([
        'No hay ningún periodo que incluya la fecha',
        'La pausa debe estar dentro de la jornada',
      ]),
    )
    expect(callsTo('PUT', `/api/workdays/${DATE}`)[0].body).toEqual({
      startTime: '08:00',
      endTime: '16:00',
      breaks: [{ type: 'COMIDA', startTime: '13:00', endTime: '13:30' }],
      location: 'CASA',
      remoteMinutes: null,
      notes: 'Reunión con cliente',
      version: null,
    })
  })

  it('valida en el navegador con los mismos mensajes y no envía nada', async () => {
    const { callsTo } = mockApi(baseRoutes())
    await openEditor()

    setTime('Entrada', '17:00')
    setTime('Salida', '08:00')
    fireEvent.click(screen.getByRole('radio', { name: 'Mixto' }))
    fireEvent.change(screen.getByLabelText('Tiempo en casa (h:mm)'), { target: { value: '7,5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Duración no válida (h:mm)')).toBeInTheDocument()
    expect(callsTo('PUT', `/api/workdays/${DATE}`)).toHaveLength(0)

    fireEvent.change(screen.getByLabelText('Tiempo en casa (h:mm)'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('La salida debe ser posterior a la entrada')).toBeInTheDocument()
    expect(callsTo('PUT', `/api/workdays/${DATE}`)).toHaveLength(0)

    setTime('Entrada', '08:00')
    setTime('Salida', '16:00')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Indica cuántos minutos has trabajado en casa')).toBeInTheDocument()
    expect(callsTo('PUT', `/api/workdays/${DATE}`)).toHaveLength(0)
  })

  it('edita un día existente enviando su versión y cierra al guardar', async () => {
    const existing = { ...referenceDay, date: DATE, version: 3 }
    const { callsTo } = mockApi({
      ...baseRoutes(existing),
      [`PUT /api/workdays/${DATE}`]: (req) => json({ ...existing, ...(req.body as object), version: 4 }),
    })
    const onClose = await openEditor()

    expect(screen.getByLabelText('Entrada')).toHaveValue('07:25')
    expect(screen.getByLabelText('Inicio de la pausa 2')).toHaveValue('15:02')
    expect(liveTotal()).toHaveTextContent('10:04')
    setTime('Salida', '18:29')
    expect(liveTotal()).toHaveTextContent('10:34')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(callsTo('PUT', `/api/workdays/${DATE}`)[0].body).toMatchObject({
      endTime: '18:29',
      version: 3,
    })
  })

  it('si el día cambió en otro sitio (409) avisa y recarga los datos actuales', async () => {
    const existing = { ...referenceDay, date: DATE, version: 3 }
    let current: WorkdayDto = existing
    mockApi({
      ...baseRoutes(),
      [`GET /api/workdays/${DATE}`]: () => json(current),
      [`PUT /api/workdays/${DATE}`]: () => {
        current = workday(DATE, '09:00', '17:00', [], { version: 5 })
        return problem(409, 'El registro ha cambiado en otra pestaña o dispositivo. Recarga y vuelve a intentarlo.')
      },
    })
    const onClose = await openEditor()

    setTime('Salida', '18:00')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText(/Este día ha cambiado en otra pestaña o dispositivo/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Entrada')).toHaveValue('09:00'))
    expect(screen.getByLabelText('Salida')).toHaveValue('17:00')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('borra el día tras confirmar', async () => {
    const existing = { ...referenceDay, date: DATE }
    const { callsTo } = mockApi({ ...baseRoutes(existing), [`DELETE /api/workdays/${DATE}`]: () => noContent() })
    const onClose = await openEditor()

    fireEvent.click(screen.getByRole('button', { name: 'Borrar día' }))
    const confirm = screen.getByRole('alertdialog', { name: 'Confirmar borrado' })
    expect(within(confirm).getByRole('button', { name: 'Sí, borrar' })).toHaveFocus()
    expect(callsTo('DELETE', `/api/workdays/${DATE}`)).toHaveLength(0)
    fireEvent.click(within(confirm).getByRole('button', { name: 'Sí, borrar' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(callsTo('DELETE', `/api/workdays/${DATE}`)).toHaveLength(1)
  })

  it('indica la ausencia del día y se cierra con Escape', async () => {
    mockApi({
      ...baseRoutes(),
      [`GET /api/absences/${DATE}`]: { date: DATE, type: 'VACACIONES', halfDay: true, note: null },
    })
    const onClose = await openEditor()

    expect(screen.getByText(/Este día tiene una ausencia/)).toHaveTextContent('Vacaciones (medio día)')
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })

  it('fuera de cualquier periodo no deja guardar', async () => {
    mockApi({ ...baseRoutes(), 'GET /api/periods': [] })
    await openEditor()

    expect(screen.getByText('Fecha fuera de cualquier periodo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled()
  })
})
