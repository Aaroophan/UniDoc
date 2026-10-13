import { useEffect, useId, useRef } from 'react'
import type { ReactNode, SyntheticEvent } from 'react'

export interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
  busy?: boolean
  disabledConfirm?: boolean
  children?: ReactNode
  onConfirm: () => void
  onCancel: () => void
}

/** Native showModal() supplies keyboard focus trapping and an inert backdrop. */
export function ConfirmDialog({
  open, title, description, confirmLabel = 'Confirm', cancelLabel = 'Cancel',
  destructive = false, busy = false, disabledConfirm = false, children, onConfirm, onCancel,
}: ConfirmDialogProps) {
  const id = useId()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)
  const previousFocus = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) {
      previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      dialog.showModal()
      // Cancel is the safest initial action, especially for terminal transitions.
      cancelRef.current?.focus()
    } else if (!open && dialog.open) {
      dialog.close()
      previousFocus.current?.focus()
    }
    return () => {
      if (dialog.open) dialog.close()
      if (open && previousFocus.current?.isConnected) previousFocus.current.focus()
    }
  }, [open])

  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    event.preventDefault()
    if (!busy) onCancel()
  }

  return (
    <dialog ref={dialogRef} className="confirm-dialog" aria-labelledby={`${id}-title`}
      aria-describedby={`${id}-description`} aria-modal="true" aria-busy={busy}
      onCancel={handleCancel}>
      <div className="confirm-dialog-inner">
        <h2 id={`${id}-title`} className="h4 mb-3">{title}</h2>
        <p id={`${id}-description`} className="text-muted mb-3">{description}</p>
        {children}
        <div className="confirm-dialog-actions d-flex justify-content-end flex-wrap gap-2 mt-4">
          <button ref={cancelRef} type="button" className="btn btn-outline-secondary" onClick={onCancel} disabled={busy}>{cancelLabel}</button>
          <button type="button" className={`btn ${destructive ? 'btn-danger' : 'btn-primary'}`}
            onClick={onConfirm} disabled={busy || disabledConfirm}>
            {busy ? <><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Please wait...</> : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}
