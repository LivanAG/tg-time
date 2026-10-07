import { createContext, useContext } from 'react'

import type { IsoDate } from '../../api/types'

export interface DayEditorContextValue {
  /** Abre el editor del día (modal) desde cualquier pantalla. */
  openDay: (date: IsoDate) => void
}

export const DayEditorContext = createContext<DayEditorContextValue | null>(null)

export function useDayEditor(): DayEditorContextValue {
  const value = useContext(DayEditorContext)
  if (!value) {
    throw new Error('useDayEditor debe usarse dentro de <DayEditorProvider>')
  }
  return value
}
