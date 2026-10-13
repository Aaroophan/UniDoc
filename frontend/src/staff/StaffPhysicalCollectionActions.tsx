import { useRef, useState } from 'react'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { formatRequestTime } from '../requests/requestFormat'
import type { StaffRequestDetail } from './staffRequestsApi'
import { localTodayISO, validateCollectionDate, validateCollectionNote } from './physicalCollectionValidation'

type DialogMode = 'ready' | 'complete'

/** Only actions returned by the API are rendered; service validation remains authoritative. */
export function StaffPhysicalCollectionActions({ details, busy, needsRefresh, fieldErrors, alert, onOpen, onReady, onComplete }: {
  details: StaffRequestDetail
  busy: boolean
  needsRefresh: boolean
  fieldErrors: Record<string, string>
  alert: { variant: 'error' | 'warning' | 'success'; message: string; requestId?: string | null } | null
  onOpen: () => void
  onReady: () => Promise<boolean>
  onComplete: (date: string, note: string) => Promise<boolean>
}) {
  const [mode, setMode] = useState<DialogMode | null>(null)
  const [openedVersion, setOpenedVersion] = useState<number | null>(null)
  const [collectionDate, setCollectionDate] = useState('')
  const [note, setNote] = useState('')
  const [dateError, setDateError] = useState<string | null>(null)
  const [noteError, setNoteError] = useState<string | null>(null)
  const [today, setToday] = useState(localTodayISO)
  const lock = useRef(false)
  const canReady = details.deliveryMethod === 'PHYSICAL' && details.availableActions.includes('READY_FOR_COLLECTION')
  const canComplete = details.deliveryMethod === 'PHYSICAL' && details.availableActions.includes('COMPLETE_COLLECTION')
  const actionStillAvailable = mode === 'ready' ? canReady : mode === 'complete' ? canComplete : false
  const staleDialog = mode !== null && openedVersion !== details.version

  function openDialog(which: DialogMode) {
    if (busy || needsRefresh || (which === 'ready' ? !canReady : !canComplete)) return
    onOpen()
    setToday(localTodayISO())
    setOpenedVersion(details.version)
    setMode(which)
    setCollectionDate('')
    setNote('')
    setDateError(null)
    setNoteError(null)
  }

  async function confirm() {
    if (!mode || !actionStillAvailable || busy || needsRefresh || staleDialog || lock.current) return
    if (mode === 'complete') {
      const invalidDate = validateCollectionDate(collectionDate)
      const invalidNote = validateCollectionNote(note)
      setDateError(invalidDate)
      setNoteError(invalidNote)
      if (invalidDate || invalidNote) {
        requestAnimationFrame(() => document.getElementById(invalidDate ? 'staff-collection-date' : 'staff-collection-note')?.focus())
        return
      }
    }
    lock.current = true
    try {
      const success = mode === 'ready' ? await onReady() : await onComplete(collectionDate, note.trim())
      if (success) setMode(null)
      // Leave input and dialog untouched on validation/transport failures.
      // If a conflict refreshed the version, the old confirmation becomes disabled.
    } finally {
      lock.current = false
    }
  }

  return <div className="staff-physical-actions" aria-labelledby="staff-physical-actions-heading">
    <h3 className="h6 mb-2" id="staff-physical-actions-heading">Physical document delivery</h3>
    {canReady && <>
      <p className="small text-muted mb-3">Check the original collection details before notifying the student that the document is ready. No file is uploaded.</p>
      <button className="btn btn-primary" type="button" disabled={busy || needsRefresh}
        onClick={() => openDialog('ready')}>Mark ready for collection</button>
    </>}
    {canComplete && <>
      <p className="small text-muted mb-3">Once the student collects the document, record the actual collection date. Completion is permanent.</p>
      <button className="btn btn-primary" type="button" disabled={busy || needsRefresh}
        onClick={() => openDialog('complete')}>Record collection</button>
    </>}
    <ConfirmDialog open={mode !== null}
      title={mode === 'ready' ? `Mark ready — ${details.requestNumber}` : `Record collection — ${details.requestNumber}`}
      description={mode === 'ready'
        ? 'Confirm the location and instructions originally supplied to the student. This moves the request to Ready and sends a student notification.'
        : 'Record when the student collected this physical document. Confirmation completes the request permanently and notifies the student.'}
      confirmLabel={mode === 'ready' ? 'Mark ready for collection' : 'Complete collection'}
      busy={busy} disabledConfirm={needsRefresh || staleDialog || !actionStillAvailable}
      onCancel={() => { setMode(null); setDateError(null); setNoteError(null) }}
      onConfirm={() => void confirm()}>
      {mode === 'ready' && <div className="staff-collection-summary">
        <p className="fw-semibold mb-2">Submitted-time collection details</p>
        <dl className="request-collection-facts mb-0">
          <div><dt>Location</dt><dd className="request-wrap">{details.collectionLocation || 'Not recorded — check this request before proceeding.'}</dd></div>
          <div><dt>Instructions</dt><dd className="request-wrap">{details.collectionInstructions || 'No instructions recorded.'}</dd></div>
        </dl>
      </div>}
      {mode === 'complete' && <div className="staff-collection-form">
        <p className="small text-muted">Ready since {formatRequestTime(details.readyAt)}. The date must not be later than today.</p>
        <div className="mb-3">
          <label className="form-label fw-semibold" htmlFor="staff-collection-date">Collection date <span aria-hidden="true">*</span></label>
          <input id="staff-collection-date" type="date" className={`form-control ${dateError || fieldErrors.collectionDate ? 'is-invalid' : ''}`}
            value={collectionDate} max={today} required disabled={busy || needsRefresh || staleDialog}
            aria-invalid={Boolean(dateError || fieldErrors.collectionDate)} aria-describedby="staff-collection-date-error"
            onChange={(event) => { setCollectionDate(event.target.value); setDateError(null) }} />
          {(dateError || fieldErrors.collectionDate) && <p id="staff-collection-date-error" className="request-inline-error mt-2" role="alert">
            {dateError || fieldErrors.collectionDate}</p>}
        </div>
        <div className="mb-3">
          <label className="form-label fw-semibold" htmlFor="staff-collection-note">Collection note (optional)</label>
          <textarea id="staff-collection-note" rows={4} maxLength={1000}
            className={`form-control ${noteError || fieldErrors.note ? 'is-invalid' : ''}`}
            value={note} disabled={busy || needsRefresh || staleDialog} aria-invalid={Boolean(noteError || fieldErrors.note)}
            aria-describedby="staff-collection-note-help staff-collection-note-error"
            onChange={(event) => { setNote(event.target.value); setNoteError(null) }} />
          <div id="staff-collection-note-help" className="form-text">Maximum 1000 characters. This note is retained in request history, not sent by email.</div>
          {(noteError || fieldErrors.note) && <p id="staff-collection-note-error" className="request-inline-error mt-2" role="alert">{noteError || fieldErrors.note}</p>}
        </div>
      </div>}
      {alert && <div className="mb-3"><FeedbackBanner variant={alert.variant} message={alert.message} requestId={alert.requestId} /></div>}
      {(staleDialog || !actionStillAvailable) && <p className="request-inline-error" role="alert">The request has changed. Close this dialog, review its current status and history, then choose a new action.</p>}
    </ConfirmDialog>
  </div>
}
