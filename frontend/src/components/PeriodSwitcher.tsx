import type { PeriodDto } from '../api/types'
import { formatDate } from '../lib/dates'
import { SelectField } from './ui'

/** Selector de periodo (calendario, resumen, festivos). */
export function PeriodPicker({
  periods,
  value,
  onChange,
  label = 'Periodo',
}: {
  periods: PeriodDto[]
  value: string
  onChange: (id: string) => void
  label?: string
}) {
  return (
    <SelectField label={label} value={value} onChange={(e) => onChange(e.target.value)} containerClassName="min-w-56">
      {periods.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name} ({formatDate(p.startDate)} – {formatDate(p.endDate)})
        </option>
      ))}
    </SelectField>
  )
}
