import { lazy, Suspense, type ReactNode } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router'

import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { Spinner } from './components/ui'
import { DayEditorProvider } from './features/workday/DayEditorProvider'
import { monthOf, todayIso } from './lib/dates'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { RecordPage } from './pages/record/RecordPage'

// Pantallas de uso menos frecuente: se cargan al entrar en ellas.
const CalendarPage = lazy(() => import('./pages/CalendarPage').then((m) => ({ default: m.CalendarPage })))
const SummaryPage = lazy(() => import('./pages/SummaryPage').then((m) => ({ default: m.SummaryPage })))
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })))

function Lazy({ children }: { children: ReactNode }) {
  return <Suspense fallback={<Spinner label="Cargando la pantalla…" />}>{children}</Suspense>
}

/** Rutas de la app. QueryClientProvider y el router los pone main.tsx (o el test). */
export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <DayEditorProvider>
                <Layout />
              </DayEditorProvider>
            </RequireAuth>
          }
        >
          <Route index element={<HomePage />} />
          <Route path="registro" element={<Navigate to={`/registro/${monthOf(todayIso())}`} replace />} />
          <Route path="registro/:month" element={<RecordPage />} />
          <Route
            path="calendario"
            element={
              <Lazy>
                <CalendarPage />
              </Lazy>
            }
          />
          <Route
            path="resumen"
            element={
              <Lazy>
                <SummaryPage />
              </Lazy>
            }
          />
          <Route
            path="ajustes"
            element={
              <Lazy>
                <SettingsPage />
              </Lazy>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

function NotFound() {
  return (
    <section className="space-y-2">
      <h1 className="text-2xl font-semibold">Página no encontrada</h1>
      <Link to="/" className="text-sky-700 underline">
        Volver al inicio
      </Link>
    </section>
  )
}
