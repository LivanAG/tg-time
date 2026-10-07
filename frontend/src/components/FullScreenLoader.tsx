export function FullScreenLoader({ label = 'Recuperando la sesión…' }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div role="status" className="flex flex-col items-center gap-3 text-slate-600">
        <span
          aria-hidden="true"
          className="h-8 w-8 animate-spin rounded-full border-4 border-sky-700 border-t-transparent"
        />
        <span className="text-sm">{label}</span>
      </div>
    </div>
  )
}
