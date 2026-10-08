import { useSelectedPeriod } from '../hooks/usePeriods'
import { formatDate } from '../lib/dates'
import { SelectField } from './ui'

/**
 * Selector global del periodo con el que se trabaja (barra lateral en escritorio, cabecera en móvil).
 * Todas las pantallas usan el periodo elegido aquí; la elección se guarda en la cuenta.
 */
export function PeriodSwitcher({ compact = false }: { compact?: boolean }) {
  const { periods, period, selectPeriod } = useSelectedPeriod()
  if (!periods || !period) {
    return null
  }
  return (
    <SelectField
      label="Periodo"
      hideLabel={compact}
      value={period.id}
      onChange={(e) => selectPeriod(e.target.value)}
      hint={compact ? undefined : `${formatDate(period.startDate)} – ${formatDate(period.endDate)}`}
      containerClassName={compact ? 'max-w-[11rem]' : undefined}
      className={compact ? 'py-1 text-sm' : undefined}
    >
      {periods.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name}
        </option>
      ))}
    </SelectField>
  )
}
