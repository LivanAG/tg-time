import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useRef, useState, type ChangeEvent } from 'react'
import { Link } from 'react-router'

import { errorMessage } from '../../api/client'
import { importExportApi } from '../../api/endpoints'
import { invalidatePeriodData } from '../../api/queryKeys'
import type { DetectedSettingsDto, ImportDayStatus, ImportOptions, ImportResultDto, PeriodDto } from '../../api/types'
import {
  Alert,
  Badge,
  Button,
  Card,
  CheckboxField,
  QueryError,
  SelectField,
  Spinner,
  type BadgeTone,
} from '../../components/ui'
import { useSelectedPeriod } from '../../hooks/usePeriods'
import { formatDate } from '../../lib/dates'
import { ABSENCE_LABELS, IMPORT_STATUS_LABELS } from '../../lib/format'
import { formatMinutes, formatOptionalMinutes } from '../../lib/time'
import { breaksSummary, timesSummary } from '../record/dayInfo'

const MAX_BYTES = 2 * 1024 * 1024

type Options = Omit<ImportOptions, 'dryRun'>

const DEFAULT_OPTIONS: Options = {
  periodId: null,
  includeFuture: false,
  markVacations: true,
  overwrite: false,
}

const STATUS_TONES: Record<ImportDayStatus, BadgeTone> = {
  NEW: 'emerald',
  EXISTS: 'slate',
  FUTURE: 'sky',
  OUT_OF_PERIOD: 'slate',
  INVALID: 'red',
  NOT_REPRESENTABLE: 'amber',
}

/** Valida el fichero en el navegador antes de subirlo (el backend vuelve a comprobarlo todo). */
function checkFile(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    return 'El fichero debe ser un Excel .xlsx'
  }
  if (file.size > MAX_BYTES) {
    return 'El fichero supera el tamaño máximo (2 MB)'
  }
  return null
}

/**
 * Importar el Excel: se sube con dryRun=true para ver la vista previa y, al confirmar, se vuelve a
 * subir el mismo fichero con dryRun=false y las opciones elegidas.
 */
export function ImportSection() {
  const queryClient = useQueryClient()
  const { periodsQuery, period: selected } = useSelectedPeriod()
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [options, setOptions] = useState<Options>(DEFAULT_OPTIONS)
  const [analyzing, setAnalyzing] = useState(false)
  const [done, setDone] = useState<ImportResultDto | null>(null)

  // Por defecto se importa en el periodo en uso (cada periodo tiene sus propios fichajes).
  const effective: Options = { ...options, periodId: options.periodId ?? selected?.id ?? null }
  const fileKey = file ? `${file.name}:${file.size}:${file.lastModified}` : null
  // Cambiar una opción vuelve a pedir la vista previa (nueva clave), manteniendo la anterior mientras tanto.
  const preview = useQuery({
    queryKey: ['import-preview', fileKey, effective],
    queryFn: () => importExportApi.importXlsx(file as File, { ...effective, dryRun: true }),
    enabled: analyzing && file !== null,
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    placeholderData: keepPreviousData,
  })

  const confirm = useMutation({
    mutationFn: () => importExportApi.importXlsx(file as File, { ...effective, dryRun: false }),
    onSuccess: (result) => {
      setDone(result)
      setAnalyzing(false)
      setFile(null)
      if (inputRef.current) {
        inputRef.current.value = ''
      }
      void invalidatePeriodData(queryClient)
    },
  })

  const onFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0] ?? null
    setDone(null)
    setAnalyzing(false)
    confirm.reset()
    if (!selected) {
      setFile(null)
      setFileError(null)
      return
    }
    const problem = checkFile(selected)
    setFileError(problem)
    setFile(problem ? null : selected)
  }

  const setOption = <K extends keyof Options>(key: K, value: Options[K]) => setOptions((o) => ({ ...o, [key]: value }))
  const periods = periodsQuery.data ?? []
  const result = analyzing ? preview.data : undefined

  return (
    <Card title="Importar el Excel" titleId="settings-import">
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          Sube tu HORAS_IZERTIS (.xlsx, máximo 2 MB). Primero verás una vista previa; no se guarda nada hasta que
          confirmes.
        </p>
        {done && (
          <Alert tone="success" title="Importación completada">
            {done.counts.imported} {done.counts.imported === 1 ? 'día importado' : 'días importados'} y{' '}
            {done.counts.vacationsCreated}{' '}
            {done.counts.vacationsCreated === 1 ? 'día de vacaciones creado' : 'días de vacaciones creados'}.{' '}
            <Link to="/" className="font-medium underline">
              Ver el inicio
            </Link>
          </Alert>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor={inputId} className="block text-sm font-medium text-slate-700">
              Fichero Excel
            </label>
            <input
              ref={inputRef}
              id={inputId}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={onFileChange}
              aria-invalid={fileError ? true : undefined}
              aria-describedby={fileError ? `${inputId}-error` : undefined}
              className="block w-full text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-sky-700 file:px-3 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-sky-800"
            />
            {fileError && (
              <p id={`${inputId}-error`} className="text-sm text-red-700">
                {fileError}
              </p>
            )}
          </div>
          <SelectField
            label="Periodo de destino"
            value={effective.periodId ?? ''}
            onChange={(e) => setOption('periodId', e.target.value === '' ? null : e.target.value)}
            hint="Los días se guardan en este periodo. Por defecto, el que estás usando."
          >
            {periods.length === 0 && <option value="">Sin periodos</option>}
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </SelectField>
        </div>

        <fieldset className="grid gap-3 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-slate-800">Opciones</legend>
          <CheckboxField
            label="Incluir días futuros"
            hint="El Excel trae los meses siguientes ya rellenos copiando semanas anteriores: normalmente no son reales."
            checked={options.includeFuture}
            onChange={(e) => setOption('includeFuture', e.target.checked)}
          />
          <CheckboxField
            label="Marcar vacaciones deducidas"
            hint="Días laborables pasados sin fichaje → VACACIONES."
            checked={options.markVacations}
            onChange={(e) => setOption('markVacations', e.target.checked)}
          />
          <CheckboxField
            label="Sobrescribir días existentes"
            hint="Si ya registraste algún día en la app, se reemplaza por el del Excel."
            checked={options.overwrite}
            onChange={(e) => setOption('overwrite', e.target.checked)}
          />
        </fieldset>

        <Button
          onClick={() => setAnalyzing(true)}
          disabled={!file || (analyzing && preview.isFetching)}
          busy={analyzing && preview.isFetching}
        >
          {analyzing ? 'Analizando…' : 'Ver la vista previa'}
        </Button>

        {analyzing && preview.isError && <QueryError error={preview.error} title="No se ha podido leer el fichero" />}
        {analyzing && preview.isPending && !preview.isError && <Spinner label="Leyendo el Excel…" />}
        {result && (
          <ImportPreview
            result={result}
            periods={periods}
            refreshing={preview.isFetching}
            confirming={confirm.isPending}
            confirmError={confirm.isError ? errorMessage(confirm.error) : null}
            onConfirm={() => confirm.mutate()}
          />
        )}
      </div>
    </Card>
  )
}

interface ImportPreviewProps {
  result: ImportResultDto
  periods: PeriodDto[]
  refreshing: boolean
  confirming: boolean
  confirmError: string | null
  onConfirm: () => void
}

function ImportPreview({ result, periods, refreshing, confirming, confirmError, onConfirm }: ImportPreviewProps) {
  const [statusFilter, setStatusFilter] = useState<ImportDayStatus | 'ALL'>('ALL')
  const period = periods.find((p) => p.id === result.periodId)
  const counts = result.counts
  const days = statusFilter === 'ALL' ? result.days : result.days.filter((d) => d.status === statusFilter)
  const statusCounts = result.days.reduce<Partial<Record<ImportDayStatus, number>>>((acc, d) => {
    acc[d.status] = (acc[d.status] ?? 0) + 1
    return acc
  }, {})
  const nothingToDo = counts.toImport === 0 && counts.vacationsToCreate === 0

  return (
    <section
      aria-label="Vista previa de la importación"
      aria-busy={refreshing || undefined}
      className={`space-y-4 border-t border-slate-200 pt-4 ${refreshing ? 'opacity-60' : ''}`}
    >
      <div>
        <h3 className="text-base font-semibold text-slate-900">Vista previa: {result.fileName}</h3>
        <p className="text-sm text-slate-600">
          Periodo de destino:{' '}
          {period ? `${period.name} (${formatDate(period.startDate)} – ${formatDate(period.endDate)})` : 'ninguno'}
        </p>
      </div>

      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Count label="Días a importar" value={counts.toImport} strong />
        <Count label="Vacaciones a crear" value={counts.vacationsToCreate} strong />
        <Count label="Futuros descartados" value={counts.skippedFuture} />
        <Count label="Ya existentes" value={counts.skippedExisting} />
        <Count label="Fuera del periodo" value={counts.skippedOutOfPeriod} />
        <Count label="No válidos" value={counts.invalid} tone={counts.invalid > 0 ? 'text-red-700' : undefined} />
        <Count
          label="Diferencias con el Excel"
          value={counts.mismatches}
          tone={counts.mismatches > 0 ? 'text-amber-700' : undefined}
        />
        <Count label="Hojas leídas" value={result.sheets.length} />
      </dl>

      {result.warnings.length > 0 && (
        <Alert tone="warning" title="Avisos">
          <ul className="list-disc pl-5">
            {result.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </Alert>
      )}

      {result.detectedSettings && <DetectedSettings settings={result.detectedSettings} period={period} />}

      <details className="rounded-lg border border-slate-200">
        <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-slate-800">
          Hojas del Excel ({result.sheets.length})
        </summary>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-600">
              <tr>
                <th scope="col" className="px-3 py-1.5">
                  Hoja
                </th>
                <th scope="col" className="px-3 py-1.5">
                  Mes
                </th>
                <th scope="col" className="px-3 py-1.5 text-right">
                  Filas
                </th>
                <th scope="col" className="px-3 py-1.5 text-right">
                  Fechas corregidas
                </th>
              </tr>
            </thead>
            <tbody>
              {result.sheets.map((sheet) => (
                <tr key={sheet.name} className="border-t border-slate-100">
                  <td className="px-3 py-1.5">{sheet.name}</td>
                  <td className="px-3 py-1.5">{sheet.month}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{sheet.rows}</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{sheet.dateCorrections}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>

      <div className="space-y-2">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <h4 className="text-sm font-semibold text-slate-800">Días ({result.days.length})</h4>
          <SelectField
            label="Filtrar por estado"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as ImportDayStatus | 'ALL')}
            containerClassName="w-56"
          >
            <option value="ALL">Todos ({result.days.length})</option>
            {(Object.keys(IMPORT_STATUS_LABELS) as ImportDayStatus[])
              .filter((s) => statusCounts[s])
              .map((s) => (
                <option key={s} value={s}>
                  {IMPORT_STATUS_LABELS[s]} ({statusCounts[s]})
                </option>
              ))}
          </SelectField>
        </div>
        <div className="max-h-[28rem] overflow-auto rounded-lg border border-slate-200">
          <table className="w-full min-w-[760px] text-sm">
            <caption className="sr-only">Días del Excel con su estado</caption>
            <thead className="sticky top-0 bg-slate-50 text-left text-xs uppercase text-slate-600">
              <tr>
                <th scope="col" className="px-3 py-1.5">
                  Fecha
                </th>
                <th scope="col" className="px-3 py-1.5">
                  Hoja
                </th>
                <th scope="col" className="px-3 py-1.5">
                  Estado
                </th>
                <th scope="col" className="px-3 py-1.5">
                  Horario
                </th>
                <th scope="col" className="px-3 py-1.5 text-right">
                  Excel
                </th>
                <th scope="col" className="px-3 py-1.5 text-right">
                  Calculado
                </th>
                <th scope="col" className="px-3 py-1.5">
                  Mensajes
                </th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => {
                const mismatch =
                  day.excelWorkedMinutes !== null &&
                  day.computedWorkedMinutes !== null &&
                  day.excelWorkedMinutes !== day.computedWorkedMinutes
                return (
                  <tr key={`${day.date}-${day.sheet}-${day.row}`} className="border-t border-slate-100 align-top">
                    <th scope="row" className="whitespace-nowrap px-3 py-1.5 text-left font-medium">
                      {formatDate(day.date)}
                    </th>
                    <td className="whitespace-nowrap px-3 py-1.5 text-slate-600">
                      {day.sheet} · fila {day.row}
                    </td>
                    <td className="px-3 py-1.5">
                      <div className="flex flex-wrap items-center gap-1">
                        <Badge tone={STATUS_TONES[day.status]}>{IMPORT_STATUS_LABELS[day.status]}</Badge>
                        <span
                          className={`text-xs ${day.action === 'IMPORT' ? 'font-semibold text-emerald-700' : 'text-slate-500'}`}
                        >
                          {day.action === 'IMPORT' ? 'Se importa' : 'Se omite'}
                        </span>
                      </div>
                    </td>
                    <td className="px-3 py-1.5 text-slate-700">
                      {day.workday?.startTime && day.workday.endTime ? (
                        <>
                          <span className="tabular-nums">{timesSummary(day.workday)}</span>
                          {day.workday.breaks.length > 0 && (
                            <span className="block text-xs text-slate-500">{breaksSummary(day.workday.breaks)}</span>
                          )}
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="px-3 py-1.5 text-right tabular-nums">
                      {formatOptionalMinutes(day.excelWorkedMinutes)}
                    </td>
                    <td
                      className={`px-3 py-1.5 text-right tabular-nums ${mismatch ? 'font-semibold text-red-700' : ''}`}
                    >
                      {formatOptionalMinutes(day.computedWorkedMinutes)}
                      {mismatch && <span className="sr-only"> (no coincide con el Excel)</span>}
                    </td>
                    <td className="px-3 py-1.5 text-xs text-slate-600">{day.messages.join(' · ')}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      {result.absences.length > 0 && (
        <details className="rounded-lg border border-slate-200" open>
          <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-slate-800">
            Vacaciones deducidas ({result.absences.length})
          </summary>
          <ul className="divide-y divide-slate-100 text-sm">
            {result.absences.map((a) => (
              <li key={a.date} className="flex flex-wrap items-center justify-between gap-2 px-3 py-1.5">
                <span>
                  {formatDate(a.date)} · {ABSENCE_LABELS[a.type]}
                  {a.reason && <span className="ml-2 text-xs text-slate-500">{a.reason}</span>}
                </span>
                <span
                  className={`text-xs ${a.action === 'IMPORT' ? 'font-semibold text-emerald-700' : 'text-slate-500'}`}
                >
                  {a.action === 'IMPORT' ? 'Se crea' : 'Se omite'}
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}

      {confirmError && <Alert tone="error">No se ha podido importar: {confirmError}</Alert>}
      <div className="flex flex-wrap items-center gap-3">
        <Button size="lg" onClick={onConfirm} busy={confirming} disabled={nothingToDo || refreshing}>
          Confirmar: importar {counts.toImport} {counts.toImport === 1 ? 'día' : 'días'} y {counts.vacationsToCreate}{' '}
          {counts.vacationsToCreate === 1 ? 'vacación' : 'vacaciones'}
        </Button>
        {nothingToDo && <span className="text-sm text-slate-600">No hay nada que importar con estas opciones.</span>}
      </div>
    </section>
  )
}

function Count({ label, value, strong, tone }: { label: string; value: number; strong?: boolean; tone?: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <dt className="text-xs text-slate-600">{label}</dt>
      <dd className={`text-xl tabular-nums ${strong ? 'font-bold' : 'font-semibold'} ${tone ?? 'text-slate-900'}`}>
        {value}
      </dd>
    </div>
  )
}

interface SettingRow {
  key: keyof DetectedSettingsDto
  label: string
  format: (value: number) => string
}

const SETTING_ROWS: SettingRow[] = [
  { key: 'agreementMinutes', label: 'Horas de convenio', format: (v) => formatMinutes(v) },
  { key: 'vacationDays', label: 'Días de vacaciones', format: String },
  { key: 'normalDayMinutes', label: 'Jornada normal', format: (v) => formatMinutes(v) },
  { key: 'intensiveDayMinutes', label: 'Jornada intensiva', format: (v) => formatMinutes(v) },
  { key: 'breakfastToleranceMin', label: 'Tolerancia de desayuno', format: (v) => `${v} min` },
  { key: 'minLunchMin', label: 'Comida mínima', format: (v) => `${v} min` },
  { key: 'maxRemotePct', label: '% máximo de teletrabajo', format: (v) => `${v} %` },
]

/** Parámetros leídos del Excel frente a los del periodo (avisa si no coinciden). */
function DetectedSettings({ settings, period }: { settings: DetectedSettingsDto; period: PeriodDto | undefined }) {
  const rows = SETTING_ROWS.filter((row) => settings[row.key] !== null)
  if (rows.length === 0) {
    return null
  }
  return (
    <details className="rounded-lg border border-slate-200">
      <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-slate-800">
        Parámetros detectados en el Excel
      </summary>
      <ul className="divide-y divide-slate-100 text-sm">
        {rows.map((row) => {
          const detected = settings[row.key] as number
          const current = period ? (period[row.key] as number) : null
          const differs = current !== null && current !== detected
          return (
            <li key={row.key} className="flex flex-wrap justify-between gap-2 px-3 py-1.5">
              <span className="text-slate-600">{row.label}</span>
              <span className={differs ? 'font-semibold text-amber-800' : 'text-slate-900'}>
                {row.format(detected)}
                {differs && ` (el periodo tiene ${row.format(current)})`}
              </span>
            </li>
          )
        })}
      </ul>
    </details>
  )
}
