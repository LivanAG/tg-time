import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { authResponse, importPreview, period } from '../../test/fixtures'
import { json, mockApi, problem, type MockRequest } from '../../test/fetchMock'
import { renderApp } from '../../test/renderApp'

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

function excelFile(name = 'HORAS_IZERTIS_2026-27.xlsx', size = 1024): File {
  return new File([new Uint8Array(size)], name, { type: XLSX })
}

function formOf(request: MockRequest): FormData {
  return request.body as FormData
}

async function chooseFile(file: File) {
  const input = await screen.findByLabelText('Fichero Excel')
  fireEvent.change(input, { target: { files: [file] } })
}

describe('Importar el Excel', () => {
  it('sube con dryRun=true, enseña la vista previa y confirma con el mismo fichero y dryRun=false', async () => {
    const { callsTo } = mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      'POST /api/import/xlsx': (req) => {
        const form = formOf(req)
        if (form.get('dryRun') === 'true') {
          const includeFuture = form.get('includeFuture') === 'true'
          return json(
            importPreview(
              includeFuture ? { counts: { ...importPreview().counts, toImport: 235, skippedFuture: 0 } } : {},
            ),
          )
        }
        return json(
          importPreview({ dryRun: false, counts: { ...importPreview().counts, imported: 168, vacationsCreated: 13 } }),
        )
      },
    })
    renderApp('/ajustes?seccion=importar')

    const file = excelFile()
    await chooseFile(file)
    fireEvent.click(screen.getByRole('button', { name: 'Ver la vista previa' }))

    const preview = await screen.findByRole('region', { name: 'Vista previa de la importación' })
    expect(
      within(preview).getByRole('heading', { name: 'Vista previa: HORAS_IZERTIS_2026-27.xlsx' }),
    ).toBeInTheDocument()
    expect(preview).toHaveTextContent('Periodo de destino: 2026-2027 (26/05/2026 – 25/05/2027)')
    expect(within(preview).getByText('Días a importar').nextElementSibling).toHaveTextContent('168')
    expect(within(preview).getByText('Vacaciones a crear').nextElementSibling).toHaveTextContent('13')
    expect(within(preview).getByText('Futuros descartados').nextElementSibling).toHaveTextContent('67')
    expect(
      within(preview).getByText('1 día con fichaje queda fuera del periodo «2026-2027»'),
    ).toBeInTheDocument()

    // Tabla de días con estado, acción y comparación con el Excel.
    const days = within(preview).getByRole('table', { name: 'Días del Excel con su estado' })
    const rows = within(days).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('26/05/2026')
    expect(rows[0]).toHaveTextContent('Mayo 26 · fila 9')
    expect(rows[0]).toHaveTextContent('Nuevo')
    expect(rows[0]).toHaveTextContent('Se importa')
    expect(rows[0]).toHaveTextContent('07:25–17:59')
    expect(rows[0]).toHaveTextContent('10:04')
    expect(rows[1]).toHaveTextContent('Futuro')
    expect(rows[1]).toHaveTextContent('Se omite')
    expect(rows[1]).toHaveTextContent('Día futuro')
    expect(preview).toHaveTextContent('Vacaciones deducidas (1)')

    // Filtrar por estado.
    fireEvent.change(within(preview).getByLabelText('Filtrar por estado'), { target: { value: 'FUTURE' } })
    expect(within(days).getAllByRole('row').slice(1)).toHaveLength(1)

    const firstUpload = formOf(callsTo('POST', '/api/import/xlsx')[0])
    expect(firstUpload.get('file')).toBe(file)
    expect(firstUpload.get('dryRun')).toBe('true')
    expect(firstUpload.get('includeFuture')).toBe('false')
    expect(firstUpload.get('markVacations')).toBe('true')
    expect(firstUpload.get('overwrite')).toBe('false')

    // Cambiar una opción vuelve a pedir la vista previa con esa opción.
    fireEvent.click(screen.getByLabelText('Incluir días futuros'))
    await waitFor(() =>
      expect(within(preview).getByText('Días a importar').nextElementSibling).toHaveTextContent('235'),
    )
    expect(formOf(callsTo('POST', '/api/import/xlsx')[1]).get('includeFuture')).toBe('true')

    fireEvent.click(within(preview).getByRole('button', { name: /Confirmar: importar 235 días y 13 vacaciones/ }))

    expect(await screen.findByText('Importación completada')).toBeInTheDocument()
    expect(screen.getByText(/168 días importados y 13 días de vacaciones creados/)).toBeInTheDocument()
    const uploads = callsTo('POST', '/api/import/xlsx')
    const confirmation = formOf(uploads[uploads.length - 1])
    expect(confirmation.get('file')).toBe(file)
    expect(confirmation.get('dryRun')).toBe('false')
    expect(confirmation.get('includeFuture')).toBe('true')
    expect(screen.queryByRole('region', { name: 'Vista previa de la importación' })).not.toBeInTheDocument()
  })

  it('rechaza en el navegador ficheros que no son .xlsx o pasan de 2 MB', async () => {
    const { callsTo } = mockApi({ 'POST /api/auth/refresh': json(authResponse()), 'GET /api/periods': [period] })
    renderApp('/ajustes?seccion=importar')

    await chooseFile(new File(['x'], 'horas.xls', { type: 'application/vnd.ms-excel' }))
    expect(screen.getByText('El fichero debe ser un Excel .xlsx')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver la vista previa' })).toBeDisabled()

    await chooseFile(excelFile('grande.xlsx', 2 * 1024 * 1024 + 1))
    expect(screen.getByText('El fichero supera el tamaño máximo (2 MB)')).toBeInTheDocument()
    expect(callsTo('POST', '/api/import/xlsx')).toHaveLength(0)
  })

  it('muestra el error del backend si el fichero no es válido', async () => {
    mockApi({
      'POST /api/auth/refresh': json(authResponse()),
      'GET /api/periods': [period],
      'POST /api/import/xlsx': () => problem(400, 'El fichero no es un .xlsx válido'),
    })
    renderApp('/ajustes?seccion=importar')

    await chooseFile(excelFile())
    fireEvent.click(screen.getByRole('button', { name: 'Ver la vista previa' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El fichero no es un .xlsx válido')
  })
})
