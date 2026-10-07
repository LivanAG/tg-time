import { useCallback, useMemo, useState, type ReactNode } from 'react'

import type { IsoDate } from '../../api/types'
import { DayEditorContext } from './DayEditorContext'
import { WorkdayEditor } from './WorkdayEditor'

/** Editor de día reutilizable desde Inicio, Registro y Calendario. */
export function DayEditorProvider({ children }: { children: ReactNode }) {
  const [date, setDate] = useState<IsoDate | null>(null)
  const openDay = useCallback((next: IsoDate) => setDate(next), [])
  const close = useCallback(() => setDate(null), [])
  const value = useMemo(() => ({ openDay }), [openDay])

  return (
    <DayEditorContext.Provider value={value}>
      {children}
      {date && <WorkdayEditor key={date} date={date} onClose={close} />}
    </DayEditorContext.Provider>
  )
}
