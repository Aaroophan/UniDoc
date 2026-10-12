import { useRef, useState } from 'react'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { formatFileSize } from '../requests/requestFormat'
import type { StaffRequestDetail } from './staffRequestsApi'
import { validateIssuedPdfCandidate } from './issuedPdfValidation'

export function StaffIssuedDocumentUpload({ details, submitting, needsRefresh, onUpload }: {
  details: StaffRequestDetail
  submitting: boolean
  needsRefresh: boolean
  onUpload: (file: File) => Promise<boolean>
}) {
  const [selected, setSelected] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const lock = useRef(false)

  async function send() {
    if (lock.current || !selected || needsRefresh) return
    const issue = validateIssuedPdfCandidate(selected)
    if (issue) { setError(issue); setConfirm(false); return }
    lock.current = true
    try {
      const success = await onUpload(selected)
      if (success) {
        setConfirm(false)
        setSelected(null)
        if (input.current) input.current.value = ''
      } else {
        setConfirm(false)
      }
    } finally {
      lock.current = false
    }
  }

  return <div className="staff-issued-upload" aria-labelledby="staff-issued-upload-title">
    <h3 className="h6 mb-2" id="staff-issued-upload-title">Upload the final electronic PDF</h3>
    <p className="small text-muted mb-3">Exactly one PDF, maximum 10 MiB. Issuing is permanent: the document cannot be replaced or deleted. The student is notified after the upload succeeds.</p>
    <label className="form-label fw-semibold" htmlFor="staff-issued-file">Issued document (PDF) <span aria-hidden="true">*</span></label>
    <input ref={input} className={`form-control ${error ? 'is-invalid' : ''}`} id="staff-issued-file"
      type="file" accept=".pdf,application/pdf" disabled={submitting || needsRefresh} aria-required="true"
      aria-invalid={Boolean(error)} aria-describedby="staff-issued-file-help staff-issued-file-error"
      onChange={(event) => {
        const file = event.currentTarget.files?.[0] ?? null
        setSelected(file)
        setError(file ? validateIssuedPdfCandidate(file) : null)
      }} />
    <div className="form-text" id="staff-issued-file-help">PDF signature, MIME type, and size are verified again by UniDoc.</div>
    {error && <p className="request-inline-error" id="staff-issued-file-error" role="alert">{error}</p>}
    {selected && <p className="small request-wrap mt-2 mb-2">Selected: <strong>{selected.name}</strong> ({formatFileSize(selected.size)})</p>}
    <button type="button" className="btn btn-primary mt-2" disabled={submitting || needsRefresh || !selected || Boolean(error)}
      onClick={() => { if (validateIssuedPdfCandidate(selected)) setError(validateIssuedPdfCandidate(selected)); else setConfirm(true) }}>
      {submitting ? 'Issuing PDF...' : 'Upload issued PDF'}</button>
    <ConfirmDialog open={confirm} title={`Issue PDF — ${details.requestNumber}`}
      description="Upload this PDF and mark the request Ready? Staff cannot replace it. The student will be notified and their first download will complete the request."
      confirmLabel="Issue document" busy={submitting} disabledConfirm={needsRefresh || Boolean(error)}
      onCancel={() => setConfirm(false)} onConfirm={() => void send()}>
      {selected && <p className="request-wrap small mb-0">{selected.name} · {formatFileSize(selected.size)}</p>}
    </ConfirmDialog>
  </div>
}
