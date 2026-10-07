import {
  forwardRef,
  useId,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'

import { errorMessage, isApiError } from '../api/client'
import { buttonClasses, type ButtonSize, type ButtonVariant } from './buttonClasses'

// Botones ----------------------------------------------------------------------------------------

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  busy?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', busy = false, className = '', disabled, children, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={buttonClasses(variant, size, className)}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy && (
        <span
          aria-hidden="true"
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  )
})

// Campos de formulario ---------------------------------------------------------------------------

const CONTROL_CLASSES =
  'block w-full rounded-lg border bg-white px-3 py-2 text-base text-slate-900 shadow-sm sm:text-sm ' +
  'focus:border-sky-600 focus:outline-none focus:ring-2 focus:ring-sky-600/30 disabled:bg-slate-100 disabled:text-slate-500'

function controlClasses(error: string | undefined, extra = ''): string {
  return `${CONTROL_CLASSES} ${error ? 'border-red-500' : 'border-slate-300'} ${extra}`
}

interface FieldChromeProps {
  id: string
  label: ReactNode
  error?: string
  hint?: ReactNode
  hideLabel?: boolean
  children: ReactNode
  className?: string
}

function FieldChrome({ id, label, error, hint, hideLabel, children, className = '' }: FieldChromeProps) {
  return (
    <div className={`space-y-1 ${className}`}>
      <label htmlFor={id} className={hideLabel ? 'sr-only' : 'block text-sm font-medium text-slate-700'}>
        {label}
      </label>
      {children}
      {hint && !error && (
        <p id={`${id}-hint`} className="text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}

function describedBy(id: string, error?: string, hint?: ReactNode): string | undefined {
  if (error) {
    return `${id}-error`
  }
  return hint ? `${id}-hint` : undefined
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode
  error?: string
  hint?: ReactNode
  hideLabel?: boolean
  containerClassName?: string
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, hideLabel, containerClassName, id, className = '', ...rest },
  ref,
) {
  const generated = useId()
  const inputId = id ?? generated
  return (
    <FieldChrome
      id={inputId}
      label={label}
      error={error}
      hint={hint}
      hideLabel={hideLabel}
      className={containerClassName}
    >
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(inputId, error, hint)}
        className={controlClasses(error, className)}
        {...rest}
      />
    </FieldChrome>
  )
})

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: ReactNode
  error?: string
  hint?: ReactNode
  hideLabel?: boolean
  containerClassName?: string
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, error, hint, hideLabel, containerClassName, id, className = '', children, ...rest },
  ref,
) {
  const generated = useId()
  const selectId = id ?? generated
  return (
    <FieldChrome
      id={selectId}
      label={label}
      error={error}
      hint={hint}
      hideLabel={hideLabel}
      className={containerClassName}
    >
      <select
        ref={ref}
        id={selectId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(selectId, error, hint)}
        className={controlClasses(error, className)}
        {...rest}
      >
        {children}
      </select>
    </FieldChrome>
  )
})

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: ReactNode
  error?: string
  hint?: ReactNode
  containerClassName?: string
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(function TextAreaField(
  { label, error, hint, containerClassName, id, className = '', ...rest },
  ref,
) {
  const generated = useId()
  const areaId = id ?? generated
  return (
    <FieldChrome id={areaId} label={label} error={error} hint={hint} className={containerClassName}>
      <textarea
        ref={ref}
        id={areaId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(areaId, error, hint)}
        className={controlClasses(error, className)}
        {...rest}
      />
    </FieldChrome>
  )
})

interface CheckboxFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode
  hint?: ReactNode
}

export const CheckboxField = forwardRef<HTMLInputElement, CheckboxFieldProps>(function CheckboxField(
  { label, hint, id, ...rest },
  ref,
) {
  const generated = useId()
  const inputId = id ?? generated
  return (
    <div className="flex items-start gap-2">
      <input
        ref={ref}
        id={inputId}
        type="checkbox"
        aria-describedby={hint ? `${inputId}-hint` : undefined}
        className="mt-0.5 h-4 w-4 rounded border-slate-300 text-sky-700 focus:ring-sky-600"
        {...rest}
      />
      <div>
        <label htmlFor={inputId} className="text-sm font-medium text-slate-800">
          {label}
        </label>
        {hint && (
          <p id={`${inputId}-hint`} className="text-xs text-slate-500">
            {hint}
          </p>
        )}
      </div>
    </div>
  )
})

// Avisos y estados ------------------------------------------------------------------------------

type AlertTone = 'error' | 'warning' | 'info' | 'success'

const ALERT_TONES: Record<AlertTone, string> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  info: 'border-sky-200 bg-sky-50 text-sky-900',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
}

export function Alert({
  tone = 'info',
  title,
  children,
  className = '',
}: {
  tone?: AlertTone
  title?: ReactNode
  children?: ReactNode
  className?: string
}) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-lg border px-3 py-2 text-sm ${ALERT_TONES[tone]} ${className}`}
    >
      {title && <p className="font-semibold">{title}</p>}
      {children}
    </div>
  )
}

export function Spinner({ label = 'Cargando…', className = '' }: { label?: string; className?: string }) {
  return (
    <div role="status" className={`flex items-center gap-2 text-sm text-slate-600 ${className}`}>
      <span
        aria-hidden="true"
        className="h-4 w-4 animate-spin rounded-full border-2 border-sky-700 border-t-transparent"
      />
      {label}
    </div>
  )
}

export function QueryError({
  error,
  onRetry,
  title = 'No se han podido cargar los datos',
}: {
  error: unknown
  onRetry?: () => void
  title?: string
}) {
  return (
    <Alert tone="error" title={title}>
      <p>{errorMessage(error)}</p>
      {onRetry && !(isApiError(error) && error.status === 404) && (
        <Button variant="secondary" size="sm" className="mt-2" onClick={onRetry}>
          Reintentar
        </Button>
      )}
    </Alert>
  )
}

/** Errores devueltos por la API que no corresponden a ningún campo del formulario. */
export function FormErrors({ messages }: { messages: string[] }) {
  if (messages.length === 0) {
    return null
  }
  return (
    <Alert tone="error">
      {messages.length === 1 ? (
        <p>{messages[0]}</p>
      ) : (
        <ul className="list-disc pl-5">
          {messages.map((m) => (
            <li key={m}>{m}</li>
          ))}
        </ul>
      )}
    </Alert>
  )
}

// Contenedores ---------------------------------------------------------------------------------

export function Card({
  title,
  actions,
  children,
  className = '',
  titleId,
}: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
  titleId?: string
}) {
  return (
    <section
      aria-labelledby={title ? titleId : undefined}
      className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${className}`}
    >
      {(title || actions) && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          {title && (
            <h2 id={titleId} className="text-base font-semibold text-slate-900">
              {title}
            </h2>
          )}
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function Stat({
  label,
  children,
  hint,
  className = '',
}: {
  label: ReactNode
  children: ReactNode
  hint?: ReactNode
  className?: string
}) {
  return (
    <div className={className}>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-lg font-semibold text-slate-900">{children}</dd>
      {hint && <dd className="text-xs text-slate-500">{hint}</dd>}
    </div>
  )
}

const BADGE_TONES = {
  slate: 'bg-slate-100 text-slate-700',
  sky: 'bg-sky-100 text-sky-800',
  emerald: 'bg-emerald-100 text-emerald-800',
  amber: 'bg-amber-100 text-amber-900',
  red: 'bg-red-100 text-red-800',
  violet: 'bg-violet-100 text-violet-800',
} as const

export type BadgeTone = keyof typeof BADGE_TONES

export function Badge({ tone = 'slate', children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${BADGE_TONES[tone]}`}>
      {children}
    </span>
  )
}
