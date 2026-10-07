import { formatMinutes } from '../lib/time'

interface DurationProps {
  minutes: number | null | undefined
  /** Con signo y color: negativos en rojo, positivos en verde (por defecto). */
  signed?: boolean
  className?: string
}

/** Duración "h:mm". Con signo (saldos y diferencias), pinta los negativos en rojo y los positivos en verde. */
export function Duration({ minutes, signed = true, className = '' }: DurationProps) {
  if (minutes === null || minutes === undefined) {
    return <span className={`text-slate-400 ${className}`}>—</span>
  }
  const tone = !signed || minutes === 0 ? '' : minutes < 0 ? 'text-red-600' : 'text-emerald-700'
  return (
    <span className={`tabular-nums ${tone} ${className}`.trim()} data-sign={signed ? Math.sign(minutes) : undefined}>
      {formatMinutes(minutes, { signed })}
    </span>
  )
}
