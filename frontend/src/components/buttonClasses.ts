export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'
export type ButtonSize = 'sm' | 'md' | 'lg'

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-sky-700 text-white hover:bg-sky-800 focus-visible:outline-sky-700 disabled:bg-sky-300',
  secondary:
    'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50 focus-visible:outline-sky-700 disabled:text-slate-400',
  danger: 'bg-red-600 text-white hover:bg-red-700 focus-visible:outline-red-600 disabled:bg-red-300',
  ghost: 'text-sky-800 hover:bg-sky-50 focus-visible:outline-sky-700 disabled:text-slate-400',
}

const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: 'px-2.5 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
  lg: 'px-5 py-3 text-base',
}

/** Clases de botón, también para enlaces con aspecto de botón. */
export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', extra = ''): string {
  return [
    'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors',
    'focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed',
    BUTTON_VARIANTS[variant],
    BUTTON_SIZES[size],
    extra,
  ].join(' ')
}
