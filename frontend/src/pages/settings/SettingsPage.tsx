import { Link, useSearchParams } from 'react-router'

import { useAuth } from '../../auth/AuthContext'
import { AdminUsersSection } from './AdminUsersSection'
import { ExportSection } from './ExportSection'
import { HolidaysSection } from './HolidaysSection'
import { ImportSection } from './ImportSection'
import { PasswordSection } from './PasswordSection'
import { PeriodsSection } from './PeriodsSection'
import { ProfileSection } from './ProfileSection'

interface Section {
  id: string
  label: string
  adminOnly?: boolean
}

const SECTIONS: Section[] = [
  { id: 'perfil', label: 'Perfil' },
  { id: 'contrasena', label: 'Contraseña' },
  { id: 'periodos', label: 'Periodos' },
  { id: 'festivos', label: 'Festivos' },
  { id: 'importar', label: 'Importar Excel' },
  { id: 'exportar', label: 'Exportar' },
  { id: 'usuarios', label: 'Usuarios', adminOnly: true },
]

/** /ajustes?seccion=... — perfil, contraseña, periodos, festivos, importar/exportar y usuarios. */
export function SettingsPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const isAdmin = user?.role === 'ADMIN'
  const sections = SECTIONS.filter((s) => !s.adminOnly || isAdmin)
  const requested = params.get('seccion')
  const current = sections.find((s) => s.id === requested)?.id ?? 'perfil'

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold text-slate-900">Ajustes</h1>
      <nav aria-label="Secciones de ajustes" className="-mx-4 overflow-x-auto px-4">
        <ul className="flex gap-1 border-b border-slate-200">
          {sections.map((section) => (
            <li key={section.id}>
              <Link
                to={`/ajustes?seccion=${section.id}`}
                aria-current={section.id === current ? 'page' : undefined}
                className={`block whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium ${
                  section.id === current
                    ? 'border-sky-700 text-sky-800'
                    : 'border-transparent text-slate-600 hover:border-slate-300 hover:text-slate-900'
                }`}
              >
                {section.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      <div>
        {current === 'perfil' && <ProfileSection />}
        {current === 'contrasena' && <PasswordSection />}
        {current === 'periodos' && <PeriodsSection />}
        {current === 'festivos' && <HolidaysSection />}
        {current === 'importar' && <ImportSection />}
        {current === 'exportar' && <ExportSection />}
        {current === 'usuarios' && isAdmin && <AdminUsersSection />}
      </div>
    </div>
  )
}
