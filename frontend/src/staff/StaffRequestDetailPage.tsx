import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { DELIVERY_METHOD_LABELS, REQUEST_STATUS_LABELS, ROLE_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { RequestStatus as RequestStatusBadge } from '../components/RequestStatus'
import { formatFileSize, formatRequestDate, formatRequestTime } from '../requests/requestFormat'
import type { StudentRequestAttachment, StudentRequestAction } from '../requests/requestsApi'
import { validateReviewText } from '../requests/reviewValidation'
import { parsePositiveId } from '../requests/requestsApi'
import { getStaffRequest, saveStaffAttachment, startStaffReview, requestStaffInformation, approveStaffRequest, rejectStaffRequest, startStaffProcessing } from './staffRequestsApi'
import type { StaffRequestDetail } from './staffRequestsApi'
import { useStaffRequestAction } from './useStaffRequestAction'
import './staff.css'

type LoadState = 'loading' | 'error' | 'not-found' | 'ready'

type ReviewAction = Extract<StudentRequestAction,
  'START_REVIEW' | 'REQUEST_INFORMATION' | 'APPROVE' | 'REJECT' | 'START_PROCESSING'>

const REVIEW_ACTIONS: readonly ReviewAction[] = ['START_REVIEW', 'REQUEST_INFORMATION', 'APPROVE', 'REJECT', 'START_PROCESSING']
const ACTION_LABELS: Record<ReviewAction, string> = {
  START_REVIEW: 'Start review', REQUEST_INFORMATION: 'Request information', APPROVE: 'Approve',
  REJECT: 'Reject', START_PROCESSING: 'Start processing',
}
const PLANNED_ACTION_LABELS: Record<string, string> = {
  UPLOAD_ISSUED_DOCUMENT: 'Upload issued PDF', READY_FOR_COLLECTION: 'Ready for collection',
  COMPLETE_COLLECTION: 'Complete collection', DOWNLOAD_ISSUED_DOCUMENT: 'Verify issued PDF',
}
const DIALOG_DESCRIPTIONS: Record<ReviewAction, string> = {
  START_REVIEW: 'Move this submitted request into review? A permanent history entry will be recorded.',
  REQUEST_INFORMATION: 'Ask the student for specific missing information. The question will be visible in their request and its history.',
  APPROVE: 'Approve the request? The student will be notified. Processing does not begin automatically.',
  REJECT: 'Reject this request permanently? The student will see your reason. Rejected requests cannot be reopened.',
  START_PROCESSING: 'Start processing this approved request? The student will be notified.',
}

function StaffRequestDetail({ id }: { id: number | null }) {
  const location = useLocation()
  const fromLocation = location.state && typeof location.state === 'object' && 'from' in location.state
    ? location.state.from : null
  const backPath = typeof fromLocation === 'string' && /^\/staff\/requests(?:\?[^#]*)?$/.test(fromLocation)
    ? fromLocation : '/staff/requests'
  const [details, setDetails] = useState<StaffRequestDetail | null>(null)
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)
  const [dialogAction, setDialogAction] = useState<ReviewAction | null>(null)
  const [decisionText, setDecisionText] = useState('')
  const [decisionError, setDecisionError] = useState<string | null>(null)
  const [busyFiles, setBusyFiles] = useState<number[]>([])
  const [downloadErrors, setDownloadErrors] = useState<Record<number, string>>({})
  const downloadLocks = useRef(new Set<number>())
  const { run, busyAction, alert, fieldErrors, needsRefresh, acknowledgeFreshDetail, clearAlert } = useStaffRequestAction(id, setDetails)

  useEffect(() => {
    if (id === null) return
    const controller = new AbortController()
    async function load() {
      setLoadState('loading')
      setLoadError(null)
      try {
        const response = await getStaffRequest(id!, controller.signal)
        if (controller.signal.aborted) return
        setDetails(response)
        acknowledgeFreshDetail()
        setLoadState('ready')
      } catch (failure: unknown) {
        if (controller.signal.aborted) return
        const api = getApiError(failure)
        if (api?.status === 404) {
          setLoadState('not-found')
          setDetails(null)
        } else {
          setLoadState('error')
          setLoadError({ message: getSafeErrorMessage(failure, 'Unable to load the request.'), requestId: api?.requestId })
        }
      }
    }
    void load()
    return () => controller.abort()
  }, [id, reload, acknowledgeFreshDetail])

  function openDecision(action: ReviewAction) {
    if (!details || !details.availableActions.includes(action) || needsRefresh || busyAction !== null) return
    clearAlert()
    setDecisionText('')
    setDecisionError(null)
    setDialogAction(action)
  }

  async function confirmDecision() {
    if (!details || !dialogAction || !details.availableActions.includes(dialogAction)) return
    const action = dialogAction
    const text = decisionText.trim()
    if (action === 'REQUEST_INFORMATION' || action === 'REJECT') {
      const invalid = validateReviewText(text, action === 'REJECT' ? 'rejection' : 'information-request')
      if (invalid) { setDecisionError(invalid); return }
    }
    setDecisionError(null)
    const command = (requestId: number, version: number) => {
      switch (action) {
        case 'START_REVIEW': return startStaffReview(requestId, version)
        case 'REQUEST_INFORMATION': return requestStaffInformation(requestId, version, text)
        case 'APPROVE': return approveStaffRequest(requestId, version)
        case 'REJECT': return rejectStaffRequest(requestId, version, text)
        case 'START_PROCESSING': return startStaffProcessing(requestId, version)
      }
    }
    const succeeded = await run(action, details.version, command)
    if (succeeded) {
      setDialogAction(null)
      setDecisionText('')
    }
  }

  async function download(attachment: StudentRequestAttachment) {
    if (id === null || downloadLocks.current.has(attachment.id)) return
    downloadLocks.current.add(attachment.id)
    setBusyFiles((current) => [...current, attachment.id])
    setDownloadErrors((current) => { const changed = { ...current }; delete changed[attachment.id]; return changed })
    try {
      await saveStaffAttachment(id, attachment)
    } catch (failure: unknown) {
      setDownloadErrors((current) => ({ ...current, [attachment.id]: getSafeErrorMessage(failure, 'Unable to download this file.') }))
    } finally {
      downloadLocks.current.delete(attachment.id)
      setBusyFiles((current) => current.filter((fileId) => fileId !== attachment.id))
    }
  }

  return <>
    <PageHeader eyebrow="Staff workspace" title={details?.requestNumber ?? 'Request details'}
      description="Review the student's account, academic profile, supporting documents, and full request history."
      actions={<div className="d-flex flex-wrap gap-2">
        <Link className="btn btn-outline-secondary" to={backPath}>Back to work list</Link>
        {id !== null && <button type="button" className="btn btn-outline-primary" disabled={busyAction !== null}
          onClick={() => { clearAlert(); setReload((n) => n + 1) }}>Refresh</button>}
      </div>} />
    {id === null || loadState === 'not-found' ? <PageState status="empty" title="Request not found"
      message="No request is available at this address." action={<Link className="btn btn-primary" to="/staff/requests">Return to work list</Link>} /> : null}
    {id !== null && loadState === 'loading' && <PageState status="loading" message="Loading request and student details..." />}
    {id !== null && loadState === 'error' && <PageState status="error" title="Unable to load request"
      message={loadError?.message ?? 'Please try again.'} requestId={loadError?.requestId} onRetry={() => setReload((n) => n + 1)} />}
    {id !== null && loadState === 'ready' && details && <>
      {alert && <div className="mb-4"><FeedbackBanner variant={alert.variant} message={alert.message} requestId={alert.requestId} onDismiss={clearAlert} /></div>}
      <section className="surface-card request-status-panel" aria-labelledby="staff-request-status">
        <div className="request-status-panel-head">
          <div><p className="section-eyebrow mb-2">Workflow status</p>
            <h2 className="h4 mb-2" id="staff-request-status">{REQUEST_STATUS_LABELS[details.status]}</h2>
            <p className="request-status-explanation mb-0">Request version {details.version}. Opening this page does not change the request status.</p>
          </div>
          <RequestStatusBadge status={details.status} />
        </div>
        {details.additionalInformationMessage && <div className="request-attention-notice"><strong>Information requested from student</strong>
          <p className="request-wrap mt-2 mb-0">{details.additionalInformationMessage}</p></div>}
        {details.rejectionReason && <div className="request-rejected-notice"><strong>Rejection reason</strong>
          <p className="request-wrap mt-2 mb-0">{details.rejectionReason}</p></div>}
      </section>
      <section className="surface-card request-detail-section staff-action-section" aria-labelledby="staff-actions-heading">
        <p className="section-eyebrow mb-2">Next steps</p>
        <h2 className="h5 mb-2" id="staff-actions-heading">Staff actions</h2>
        {needsRefresh && <p role="status" className="request-inline-error">Refresh the request successfully before attempting any further action.</p>}
        {details.availableActions.length === 0 && <p className="request-callout-intro mb-0">No further actions are available for this request.</p>}
        <div className="d-flex align-items-center gap-3 flex-wrap">
          {REVIEW_ACTIONS.filter((action) => details.availableActions.includes(action)).map((action) =>
            <button type="button" key={action}
              className={`btn ${action === 'REJECT' ? 'btn-outline-danger' : action === 'REQUEST_INFORMATION' ? 'btn-outline-primary' : 'btn-primary'}`}
              disabled={busyAction !== null || needsRefresh}
              onClick={() => openDecision(action)}>{ACTION_LABELS[action]}</button>)}
        </div>
        {details.availableActions.some((action) => !REVIEW_ACTIONS.includes(action as ReviewAction)) && <div className="staff-future-actions">
          <p className="small text-muted mb-2">The following delivery actions become available in the next implementation phase.</p>
          <div className="d-flex flex-wrap gap-2">
            {details.availableActions.filter((action) => !REVIEW_ACTIONS.includes(action as ReviewAction)).map((action) =>
              <span className="staff-planned-action" key={action}>{PLANNED_ACTION_LABELS[action] || action}</span>)}
          </div>
        </div>}
      </section>
      <div className="request-detail-layout">
        <div className="request-detail-primary">
          <section className="surface-card request-detail-section" aria-labelledby="staff-student-heading">
            <h2 className="h5 mb-4" id="staff-student-heading">Student and academic profile</h2>
            <dl className="request-detail-facts staff-profile-grid">
              <div><dt>Full name</dt><dd className="request-wrap">{details.student.fullName}</dd></div>
              <div><dt>Registration number</dt><dd className="request-wrap">{details.student.registrationNumber ?? 'Not recorded'}</dd></div>
              <div><dt>Email</dt><dd className="request-wrap">{details.student.email}</dd></div>
              <div><dt>Phone</dt><dd className="request-wrap">{details.student.phone || 'Not provided'}</dd></div>
              <div><dt>Faculty</dt><dd className="request-wrap">{details.student.faculty || 'Not recorded'}</dd></div>
              <div><dt>Department</dt><dd className="request-wrap">{details.student.department || 'Not recorded'}</dd></div>
              <div><dt>Degree programme</dt><dd className="request-wrap">{details.student.degreeProgramme || 'Not recorded'}</dd></div>
              <div><dt>Academic year</dt><dd>{details.student.academicYear ?? 'Not recorded'}</dd></div>
            </dl>
          </section>
          <section className="surface-card request-detail-section" aria-labelledby="staff-request-overview">
            <h2 className="h5 mb-4" id="staff-request-overview">Request overview</h2>
            <dl className="request-detail-facts">
              <div><dt>Document type (at submission)</dt><dd className="request-wrap">{details.documentTypeName}</dd></div>
              <div><dt>Delivery</dt><dd>{DELIVERY_METHOD_LABELS[details.deliveryMethod]}</dd></div>
              <div><dt>Required by</dt><dd>{formatRequestDate(details.requiredDate)}</dd></div>
              <div><dt>Submitted</dt><dd>{formatRequestTime(details.createdAt)}</dd></div>
              <div><dt>Last updated</dt><dd>{formatRequestTime(details.updatedAt)}</dd></div>
              {details.readyAt && <div><dt>Ready on</dt><dd>{formatRequestTime(details.readyAt)}</dd></div>}
              {details.completedAt && <div><dt>Completed on</dt><dd>{formatRequestTime(details.completedAt)}</dd></div>}
            </dl>
            <div className="request-purpose-view"><h3 className="h6 mb-2">Purpose</h3><p className="request-wrap mb-0">{details.purpose}</p></div>
          </section>
          {details.deliveryMethod === 'PHYSICAL' && <section className="surface-card request-detail-section" aria-labelledby="staff-collection">
            <h2 className="h5 mb-3" id="staff-collection">Physical collection</h2>
            <dl className="request-collection-facts">
              <div><dt>Location (at submission)</dt><dd className="request-wrap">{details.collectionLocation || 'Not specified'}</dd></div>
              <div><dt>Instructions (at submission)</dt><dd className="request-wrap">{details.collectionInstructions || 'Not specified'}</dd></div>
              {details.collectionDate && <div><dt>Collected on</dt><dd>{formatRequestDate(details.collectionDate)}</dd></div>}
              {details.collectionNote && <div><dt>Collection note</dt><dd className="request-wrap">{details.collectionNote}</dd></div>}
            </dl>
          </section>}
          {details.deliveryMethod === 'ELECTRONIC' && <section className="surface-card request-detail-section" aria-labelledby="staff-issued">
            <h2 className="h5 mb-3" id="staff-issued">Issued electronic document</h2>
            {details.issuedDocument ? <dl className="request-collection-facts">
              <div><dt>Original filename</dt><dd className="request-wrap">{details.issuedDocument.originalFilename}</dd></div>
              <div><dt>Size</dt><dd>{formatFileSize(details.issuedDocument.sizeBytes)}</dd></div>
              <div><dt>Created</dt><dd>{formatRequestTime(details.issuedDocument.createdAt)}</dd></div>
              <div><dt>Student downloads</dt><dd>{details.issuedDocument.downloadCount}</dd></div>
              {details.issuedDocument.firstDownloadedAt && <div><dt>First download</dt><dd>{formatRequestTime(details.issuedDocument.firstDownloadedAt)}</dd></div>}
            </dl> : <p className="request-callout-intro mb-0">No issued document has been uploaded.</p>}
            {details.issuedDocument && <p className="small text-muted mb-0">Staff PDF verification download is introduced in the electronic-delivery phase.</p>}
          </section>}
          <section className="surface-card request-detail-section" aria-labelledby="staff-files">
            <h2 className="h5 mb-2" id="staff-files">Supporting attachments</h2>
            <p className="request-callout-intro">Attachments are private and retrieved through the authenticated staff API.</p>
            {details.attachments.length === 0 ? <p className="text-muted mb-0">No supporting attachments.</p> :
              <ul className="request-download-list">{details.attachments.map((file) => <li className="request-download-item" key={file.id}>
                <div className="min-width-0"><strong className="request-wrap d-block">{file.originalFilename}</strong>
                  <span className="small text-muted">{file.kind === 'INITIAL_SUPPORT' ? 'Initial supporting document' : 'Information response'} · {formatFileSize(file.sizeBytes)} · {formatRequestTime(file.createdAt)}</span>
                  {downloadErrors[file.id] && <p role="alert" className="request-inline-error request-wrap">{downloadErrors[file.id]}</p>}
                </div>
                <button type="button" className="btn btn-outline-primary btn-sm" disabled={busyFiles.includes(file.id)}
                  onClick={() => void download(file)} aria-label={`Download ${file.originalFilename}`}>
                  {busyFiles.includes(file.id) ? 'Downloading...' : 'Download'}</button>
              </li>)}</ul>}
          </section>
        </div>
        <div className="request-detail-aside">
          <section className="surface-card request-aside-card" aria-labelledby="staff-history">
            <h2 className="h5 mb-4" id="staff-history">Status history</h2>
            {details.history.length === 0 ? <p className="small text-muted mb-0">No history recorded.</p> :
              <ol className="request-history-list">{details.history.map((event) => <li key={event.id} className="request-history-item">
                <span className="request-history-marker" aria-hidden="true" />
                <h3 className="h6 mb-1">{REQUEST_STATUS_LABELS[event.newStatus]}</h3>
                <div className="small text-muted">{formatRequestTime(event.createdAt)}</div>
                <div className="small text-muted request-wrap">By {event.changedBy.fullName} ({ROLE_LABELS[event.changedBy.role]})</div>
                {event.previousStatus && <div className="small text-muted">From {REQUEST_STATUS_LABELS[event.previousStatus]}</div>}
                {event.note && <p className="request-wrap mb-0 mt-2">{event.note}</p>}
              </li>)}</ol>}
          </section>
          <section className="surface-card request-aside-card" aria-labelledby="staff-version">
            <h2 className="h6 mb-2" id="staff-version">Concurrency and access</h2>
            <p className="small text-muted mb-2">Request version: <strong>{details.version}</strong></p>
            <p className="small text-muted mb-0">All active staff may review this request. If another staff member updates it, a conflicting action will be rejected and the detail reloaded.</p>
          </section>
        </div>
      </div>
      <ConfirmDialog open={dialogAction !== null}
        title={dialogAction ? `${ACTION_LABELS[dialogAction]} — ${details.requestNumber}` : 'Staff decision'}
        description={dialogAction ? DIALOG_DESCRIPTIONS[dialogAction] : ''}
        confirmLabel={dialogAction ? ACTION_LABELS[dialogAction] : 'Confirm'}
        destructive={dialogAction === 'REJECT'} busy={busyAction !== null}
        disabledConfirm={needsRefresh || (dialogAction !== null && !details.availableActions.includes(dialogAction))}
        onCancel={() => { setDialogAction(null); setDecisionError(null) }}
        onConfirm={() => void confirmDecision()}>
        {(dialogAction === 'REQUEST_INFORMATION' || dialogAction === 'REJECT') && <div className="mb-3">
          <label className="form-label fw-semibold" htmlFor="staff-decision-text">
            {dialogAction === 'REJECT' ? 'Reason for rejection' : 'What information is needed?'} <span aria-hidden="true">*</span>
          </label>
          <textarea id="staff-decision-text" className={`form-control ${decisionError || fieldErrors[dialogAction === 'REJECT' ? 'reason' : 'message'] ? 'is-invalid' : ''}`}
            rows={5} minLength={5} maxLength={2000} value={decisionText}
            aria-required="true" aria-invalid={Boolean(decisionError || fieldErrors[dialogAction === 'REJECT' ? 'reason' : 'message'])}
            aria-describedby="staff-decision-guidance staff-decision-error"
            disabled={busyAction !== null}
            onChange={(event) => { setDecisionText(event.target.value); setDecisionError(null) }} />
          <p id="staff-decision-guidance" className="small text-muted mt-1 mb-1">5–2000 characters. Saved as plain text in request history.</p>
          {(decisionError || fieldErrors[dialogAction === 'REJECT' ? 'reason' : 'message']) &&
            <p id="staff-decision-error" role="alert" className="request-inline-error">
              {decisionError || fieldErrors[dialogAction === 'REJECT' ? 'reason' : 'message']}</p>}
        </div>}
        {alert && <div className="mb-2"><FeedbackBanner variant={alert.variant} message={alert.message} requestId={alert.requestId} /></div>}
        {dialogAction && !details.availableActions.includes(dialogAction) &&
          <p role="alert" className="request-inline-error">This action is no longer available. Close this dialog and review the latest status.</p>}
      </ConfirmDialog>
    </>}
  </>
}

export function StaffRequestDetailPage() {
  const { requestId } = useParams()
  return <StaffRequestDetail key={requestId} id={parsePositiveId(requestId)} />
}
