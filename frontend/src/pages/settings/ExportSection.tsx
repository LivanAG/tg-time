import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'

import { errorMessage, saveBlob } from '../../api/client'
import { importExportApi } from '../../api/endpoints'
import { Alert, Button, Card, TextField } from '../../components/ui'
import { isYearMonth, monthOf, splitMonth, todayIso } from '../../lib/dates'

/** Descarga un mes con el formato de la hoja mensual del Excel (se puede volver a importar). */
export function ExportSection() {
  const [month, setMonth] = useState(monthOf(todayIso()))
  const [error, setError] = useState<string | null>(null)
  const download = useMutation({
    mutationFn: () => {
      const { year, month: m } = splitMonth(month)
      return importExportApi.exportXlsx(year, m)
    },
    onSuccess: saveBlob,
    onError: (e) => setError(errorMessage(e)),
  })

  return (
    <Card title="Exportar a Excel" titleId="settings-export">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (!isYearMonth(month)) {
            setError('Elige un mes')
            return
          }
          setError(null)
          download.mutate()
        }}
        noValidate
        className="flex max-w-lg flex-wrap items-end gap-3"
        aria-label="Exportar un mes"
      >
        <TextField
          label="Mes"
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          containerClassName="w-48"
        />
        <Button type="submit" busy={download.isPending}>
          Descargar Excel
        </Button>
      </form>
      <p className="mt-3 text-sm text-slate-600">
        Genera horas-AAAA-MM.xlsx con las mismas columnas que tu hoja mensual, para archivarlo o volver a importarlo.
      </p>
      {error && (
        <Alert tone="error" className="mt-3">
          No se ha podido exportar: {error}
        </Alert>
      )}
    </Card>
  )
}
