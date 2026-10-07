import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** Pila de diálogos abiertos: Escape y el ciclo de Tab solo afectan al de arriba. */
const openDialogs: string[] = []

interface ModalProps {
  title: ReactNode
  description?: ReactNode
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'md' | 'lg' | 'xl'
  /** Elemento que recibe el foco al abrir (por defecto, el propio diálogo). */
  initialFocusRef?: RefObject<HTMLElement | null>
}

const SIZES = { md: 'sm:max-w-lg', lg: 'sm:max-w-2xl', xl: 'sm:max-w-4xl' }

/**
 * Diálogo modal accesible: role="dialog" + aria-modal, foco dentro al abrir, Tab atrapado, Escape
 * cierra y el foco vuelve al elemento que lo abrió. En móvil ocupa la pantalla; en escritorio es un panel.
 */
export function Modal({ title, description, onClose, children, footer, size = 'lg', initialFocusRef }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const dialogId = titleId
    openDialogs.push(dialogId)
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const node = dialogRef.current
    ;(initialFocusRef?.current ?? node)?.focus()

    const isTop = () => openDialogs[openDialogs.length - 1] === dialogId
    const onKeyDown = (event: KeyboardEvent) => {
      if (!isTop() || !node) {
        return
      }
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      if (event.key === 'Tab') {
        const focusables = Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE))
        if (focusables.length === 0) {
          event.preventDefault()
          node.focus()
          return
        }
        const first = focusables[0]
        const last = focusables[focusables.length - 1]
        const active = document.activeElement
        if (event.shiftKey && (active === first || active === node)) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && active === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      const index = openDialogs.lastIndexOf(dialogId)
      if (index >= 0) {
        openDialogs.splice(index, 1)
      }
      if (openDialogs.length === 0) {
        document.body.style.overflow = previousOverflow
      }
      previouslyFocused?.focus()
    }
    // titleId y la ref son estables: el efecto solo corre al montar y desmontar.
  }, [titleId, initialFocusRef])

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4">
      <div aria-hidden="true" className="absolute inset-0 bg-slate-900/50" onClick={() => onCloseRef.current()} />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className={`relative flex max-h-[100dvh] w-full flex-col bg-white shadow-xl outline-none sm:max-h-[90vh] sm:rounded-xl ${SIZES[size]}`}
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-4 py-3">
          <div>
            <h2 id={titleId} className="text-lg font-semibold text-slate-900">
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="text-sm text-slate-600">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={() => onCloseRef.current()}
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-sky-700"
            aria-label="Cerrar"
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 20 20"
              className="h-5 w-5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path d="M5 5l10 10M15 5L5 15" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-4 py-4">{children}</div>
        {footer && <div className="border-t border-slate-200 px-4 py-3">{footer}</div>}
      </div>
    </div>,
    document.body,
  )
}
