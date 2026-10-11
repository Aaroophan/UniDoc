import { useEffect, useRef } from 'react'
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

/** Native modal dialog provides focus trapping, inert backdrop and Escape. */
export function ConfirmDialog({
  open, title, description, confirmLabel = 'Confirm', cancelLabel = 'Cancel',
  destructive = false, busy = false, disabledConfirm = false, children, onConfirm, onCancel,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const oldFocus = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) {
      oldFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
      oldFocus.current?.focus()
    }
    return () => {
      if (dialog.open) dialog.close()
      oldFocus.current?.focus()
    }
  }, [open])

  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    event.preventDefault()
    if (!busy) onCancel()
  }

  return (
    <dialog ref={dialogRef} className="confirm-dialog" aria-labelledby="confirm-title" aria-describedby="confirm-description" onCancel={handleCancel}>
      <div className="confirm-dialog-inner">
        <h2 id="confirm-title" className="h4 mb-3">{title}</h2>
        <p id="confirm-description" className="text-muted mb-3">{description}</p>
        {children}
        <div className="d-flex justify-content-end flex-wrap gap-2 mt-4">
          <button type="button" className="btn btn-outline-secondary" onClick={onCancel} disabled={busy}>{cancelLabel}</button>
          <button type="button" className={`btn ${destructive ? 'btn-danger' : 'btn-primary'}`} onClick={onConfirm} disabled={busy || disabledConfirm}>
            {busy ? 'Please wait...' : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}
