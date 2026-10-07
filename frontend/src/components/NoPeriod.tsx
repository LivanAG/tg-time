import { Link } from 'react-router'

import { Alert } from './ui'

/** Aviso cuando el usuario aún no tiene periodos. */
export function NoPeriod() {
  return (
    <Alert tone="info" title="Aún no tienes ningún periodo">
      Créalo en{' '}
      <Link to="/ajustes?seccion=periodos&nuevo=1" className="font-medium underline">
        Ajustes → Periodos
      </Link>{' '}
      (el formulario trae los valores de tu Excel).
    </Alert>
  )
}
