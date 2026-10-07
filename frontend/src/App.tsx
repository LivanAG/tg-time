import { Link, Route, Routes } from 'react-router'

import { ApiStatus } from './components/ApiStatus'

export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="text-lg font-semibold">
            Control Horario
          </Link>
          <ApiStatus />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </div>
  )
}

function Home() {
  return (
    <section className="space-y-2">
      <h1 className="text-2xl font-semibold">Control Horario</h1>
      <p className="text-slate-600">
        Base del proyecto lista. El acceso, el registro diario y los resúmenes llegan en las siguientes fases.
      </p>
    </section>
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
