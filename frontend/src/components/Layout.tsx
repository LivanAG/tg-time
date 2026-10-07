import type { ReactNode } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'

import { useAuth } from '../auth/AuthContext'
import { useIsDesktop } from '../hooks/useMediaQuery'
import { monthOf, todayIso } from '../lib/dates'
import { Button } from './ui'

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  end?: boolean
}

function Icon({ d }: { d: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="h-6 w-6"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={d} />
    </svg>
  )
}

function navItems(): NavItem[] {
  return [
    {
      to: '/',
      label: 'Inicio',
      end: true,
      icon: <Icon d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" />,
    },
    {
      to: `/registro/${monthOf(todayIso())}`,
      label: 'Registro',
      icon: (
        <Icon d="M8 3v3M16 3v3M4 8h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM8 12h3M8 16h6" />
      ),
    },
    {
      to: '/calendario',
      label: 'Calendario',
      icon: <Icon d="M4 5h16v15H4zM4 9h16M9 9v11M14 9v11M4 14.5h16" />,
    },
    { to: '/resumen', label: 'Resumen', icon: <Icon d="M4 20V10M10 20V4M16 20v-7M22 20H2" /> },
    {
      to: '/ajustes',
      label: 'Ajustes',
      icon: (
        <Icon d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
      ),
    },
  ]
}

/** Navegación inferior en móvil y lateral en escritorio (solo se pinta la que toca). */
export function Layout() {
  const { user, logout } = useAuth()
  const isDesktop = useIsDesktop()
  const { pathname } = useLocation()
  const items = navItems()
  // El enlace de Registro apunta al mes actual, pero debe marcarse en cualquier mes.
  const isActiveItem = (item: NavItem, isActive: boolean) =>
    isActive || (item.to.startsWith('/registro') && pathname.startsWith('/registro'))

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 md:flex">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2"
      >
        Saltar al contenido
      </a>

      {/* Escritorio: barra lateral */}
      {isDesktop && (
        <aside className="sticky top-0 flex h-screen w-60 shrink-0 flex-col border-r border-slate-200 bg-white">
          <div className="px-5 py-5">
            <p className="text-lg font-semibold text-slate-900">Control Horario</p>
            {user && <p className="truncate text-sm text-slate-500">{user.name}</p>}
          </div>
          <nav aria-label="Principal" className="flex-1 px-3">
            <ul className="space-y-1">
              {items.map((item) => (
                <li key={item.label}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium ${
                        isActiveItem(item, isActive) ? 'bg-sky-50 text-sky-800' : 'text-slate-700 hover:bg-slate-100'
                      }`
                    }
                  >
                    {item.icon}
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <div className="border-t border-slate-200 p-3">
            <Button variant="ghost" className="w-full justify-start" onClick={() => void logout()}>
              Cerrar sesión
            </Button>
          </div>
        </aside>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Móvil: cabecera */}
        {!isDesktop && (
          <header className="sticky top-0 z-30 flex items-center justify-between border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur">
            <span className="text-base font-semibold">Control Horario</span>
            <Button variant="ghost" size="sm" onClick={() => void logout()}>
              Salir
            </Button>
          </header>
        )}

        <main id="contenido" className="mx-auto w-full max-w-6xl flex-1 px-4 pb-24 pt-4 md:px-8 md:pb-10 md:pt-8">
          <Outlet />
        </main>

        {/* Móvil: navegación inferior */}
        {!isDesktop && (
          <nav
            aria-label="Principal"
            className="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)]"
          >
            <ul className="grid grid-cols-5">
              {items.map((item) => (
                <li key={item.label}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `flex flex-col items-center gap-0.5 px-1 py-2 text-[11px] font-medium ${
                        isActiveItem(item, isActive) ? 'text-sky-700' : 'text-slate-500'
                      }`
                    }
                  >
                    {item.icon}
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        )}
      </div>
    </div>
  )
}
