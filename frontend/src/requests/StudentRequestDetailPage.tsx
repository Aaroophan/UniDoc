import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { DELIVERY_METHOD_LABELS, REQUEST_STATUS_LABELS, ROLE_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { RequestStatus } from '../components/RequestStatus'
import { formatFileSize, formatRequestDate, formatRequestTime, statusExplanation } from './requestFormat'
import { getStudentRequest, parsePositiveId, saveStudentAttachment } from './requestsApi'
import type { StudentRequestDetail, StudentRequestAttachment } from './requestsApi'
import './requests.css'

type LoadState = 'loading' | 'error' | 'not-found' | 'ready'

function Facts({ details }: { details: StudentRequestDetail }) {
  return (
    <dl className="request-detail-facts">
      <div><dt>Document type</dt><dd className="request-wrap">{details.documentTypeName}</dd></div>
      <div><dt>Delivery</dt><dd>{DELIVERY_METHOD_LABELS[details.deliveryMethod]}</dd></div>
      <div><dt>Required by</dt><dd>{formatRequestDate(details.requiredDate)}</dd></div>
      <div><dt>Submitted</dt><dd>{formatRequestTime(details.createdAt)}</dd></div>
      <div><dt>Last updated</dt><dd>{formatRequestTime(details.updatedAt)}</dd></div>
      {details.readyAt && <div><dt>Ready on</dt><dd>{formatRequestTime(details.readyAt)}</dd></div>}
      {details.completedAt && <div><dt>Completed on</dt><dd>{formatRequestTime(details.completedAt)}</dd></div>}
    </dl>
  )
}

export function StudentRequestDetailPage() {
  const { requestId } = useParams()
  const id = parsePositiveId(requestId)
  const location = useLocation()
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [details, setDetails] = useState<StudentRequestDetail | null>(null)
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)
  const [pendingFiles, setPendingFiles] = useState<number[]>([])
  const [downloadErrors, setDownloadErrors] = useState<Record<number, string>>({})
  const downloadLocks = useRef(new Set<number>())

  useEffect(() => {
    const controller = new AbortController()
    if (id === null) return () => controller.abort()
    async function load() {
      setLoadState('loading')
      setLoadError(null)
      try {
        const response = await getStudentRequest(id!, controller.signal)
        if (controller.signal.aborted) return
        setDetails(response)
        setLoadState('ready')
      } catch (failure: unknown) {
        if (controller.signal.aborted) return
        const apiError = getApiError(failure)
        if (apiError?.status === 404) {
          // Same response and wording for a nonexistent request or one owned by another student.
          setLoadState('not-found')
          setDetails(null)
        } else {
          setLoadError({ message: getSafeErrorMessage(failure, 'Unable to load request details.'), requestId: apiError?.requestId })
          setLoadState('error')
        }
      }
    }
    void load()
    return () => controller.abort()
  }, [id, reload])

  async function download(attachment: StudentRequestAttachment) {
    if (id === null || downloadLocks.current.has(attachment.id)) return
    downloadLocks.current.add(attachment.id)
    setPendingFiles((previous) => [...previous, attachment.id])
    setDownloadErrors((previous) => { const next = { ...previous }; delete next[attachment.id]; return next })
    try {
      await saveStudentAttachment(id, attachment)
    } catch (failure: unknown) {
      setDownloadErrors((previous) => ({ ...previous, [attachment.id]: getSafeErrorMessage(failure, 'Unable to download this attachment.') }))
    } finally {
      downloadLocks.current.delete(attachment.id)
      setPendingFiles((previous) => previous.filter((pending) => pending !== attachment.id))
    }
  }

  const visibleLoadState: LoadState = id === null ? 'not-found' : loadState
  const submittedNotice = location.state && typeof location.state === 'object' && 'submitted' in location.state && location.state.submitted === true
  return (
    <>
      <PageHeader eyebrow="Student workspace" title={details?.requestNumber ?? 'Request details'}
        description="Track your request status, delivery information, supporting documents, and complete history."
        actions={<div className="d-flex flex-wrap gap-2">
          <Link className="btn btn-outline-secondary" to="/requests">Back to My requests</Link>
          {visibleLoadState === 'ready' && <button type="button" className="btn btn-outline-primary" onClick={() => setReload((current) => current + 1)}>Refresh</button>}
        </div>} />
      {submittedNotice && visibleLoadState === 'ready' && <div className="mb-4"><FeedbackBanner variant="success" message="Your request was submitted successfully. Keep the request number for tracking." /></div>}
      {visibleLoadState === 'loading' && <PageState status="loading" message="Loading request details..." />}
      {visibleLoadState === 'error' && <PageState status="error" title="Unable to load request"
        message={loadError?.message ?? 'Please try again.'} requestId={loadError?.requestId} onRetry={() => setReload((current) => current + 1)} />}
      {visibleLoadState === 'not-found' && <PageState status="empty" title="Request not found"
        message="This request could not be found or is not available to this account."
        action={<Link className="btn btn-primary" to="/requests">Return to My requests</Link>} />}
      {visibleLoadState === 'ready' && details && (
        <>
          <section className="surface-card request-status-panel" aria-labelledby="request-status-title">
            <div className="request-status-panel-head">
              <div>
                <p className="section-eyebrow mb-2">Current progress</p>
                <h2 className="h4 mb-2" id="request-status-title">{REQUEST_STATUS_LABELS[details.status]}</h2>
                <p className="request-status-explanation mb-0">{statusExplanation(details.status, details.deliveryMethod)}</p>
              </div>
              <RequestStatus status={details.status} />
            </div>
            {details.status === 'ADDITIONAL_INFORMATION_REQUIRED' && details.additionalInformationMessage && (
              <div className="request-attention-notice" role="status">
                <strong>Message from the university</strong>
                <p className="request-wrap mt-2 mb-0">{details.additionalInformationMessage}</p>
                {details.availableActions.includes('RESPOND') && <p className="small mt-2 mb-0">Online responses to staff information requests are not enabled yet. Please contact the university for guidance.</p>}
              </div>
            )}
            {details.status === 'REJECTED' && details.rejectionReason && (
              <div className="request-rejected-notice"><strong>Reason for rejection</strong><p className="request-wrap mt-2 mb-0">{details.rejectionReason}</p></div>
            )}
          </section>
          <div className="request-detail-layout">
            <div className="request-detail-primary">
              <section className="surface-card request-detail-section" aria-labelledby="request-detail-overview-title">
                <h2 className="h5 mb-4" id="request-detail-overview-title">Request overview</h2>
                <Facts details={details} />
                <div className="request-purpose-view"><h3 className="h6 mb-2">Purpose</h3><p className="request-wrap mb-0">{details.purpose}</p></div>
              </section>
              {details.deliveryMethod === 'PHYSICAL' && (
                <section className="surface-card request-detail-section" aria-labelledby="request-collection-title">
                  <h2 className="h5 mb-2" id="request-collection-title">Physical collection</h2>
                  {details.status === 'READY' || details.status === 'COMPLETED' ? (
                    <p className="request-callout-intro">Your document {details.status === 'COMPLETED' ? 'was recorded as collected' : 'can be collected'} at the location below.</p>
                  ) : <p className="request-callout-intro">Collection will be available once staff mark the document as ready.</p>}
                  <dl className="request-collection-facts">
                    <div><dt>Location</dt><dd className="request-wrap">{details.collectionLocation || 'Not specified'}</dd></div>
                    {details.collectionInstructions && <div><dt>Instructions</dt><dd className="request-wrap">{details.collectionInstructions}</dd></div>}
                    {details.collectionDate && <div><dt>Collection date</dt><dd>{formatRequestDate(details.collectionDate)}</dd></div>}
                    {details.collectionNote && <div><dt>Collection note</dt><dd className="request-wrap">{details.collectionNote}</dd></div>}
                  </dl>
                </section>
              )}
              {details.deliveryMethod === 'ELECTRONIC' && (
                <section className="surface-card request-detail-section" aria-labelledby="request-issued-title">
                  <h2 className="h5 mb-2" id="request-issued-title">Electronic document</h2>
                  {details.issuedDocument ? (
                    <>
                      <p className="request-callout-intro">Document metadata is available. Online issued-document downloading is not enabled in this release.</p>
                      <dl className="request-collection-facts">
                        <div><dt>Document name</dt><dd className="request-wrap">{details.issuedDocument.originalFilename}</dd></div>
                        <div><dt>Size</dt><dd>{formatFileSize(details.issuedDocument.sizeBytes)}</dd></div>
                        <div><dt>Issued on</dt><dd>{formatRequestTime(details.issuedDocument.createdAt)}</dd></div>
                        <div><dt>Recorded downloads</dt><dd>{details.issuedDocument.downloadCount}</dd></div>
                        {details.issuedDocument.firstDownloadedAt && <div><dt>First downloaded</dt><dd>{formatRequestTime(details.issuedDocument.firstDownloadedAt)}</dd></div>}
                      </dl>
                    </>
                  ) : <p className="request-callout-intro mb-0">No issued document is available yet. Your supporting attachments are listed separately below.</p>}
                </section>
              )}
              <section className="surface-card request-detail-section" aria-labelledby="request-attachments-title">
                <div className="request-section-header"><h2 className="h5 mb-0" id="request-attachments-title">Supporting documents</h2><span className="small text-muted">{details.attachments.length} {details.attachments.length === 1 ? 'file' : 'files'}</span></div>
                {details.attachments.length === 0 ? <p className="text-muted mb-0">No supporting documents were attached to this request.</p> : (
                  <ul className="request-download-list">
                    {details.attachments.map((attachment) => (
                      <li key={attachment.id} className="request-download-item">
                        <div className="min-width-0">
                          <strong className="request-wrap d-block">{attachment.originalFilename}</strong>
                          <span className="small text-muted">{attachment.kind === 'INITIAL_SUPPORT' ? 'Submitted with request' : 'Additional information'} · {formatFileSize(attachment.sizeBytes)} · {formatRequestTime(attachment.createdAt)}</span>
                          {downloadErrors[attachment.id] && <p className="request-inline-error" role="alert">{downloadErrors[attachment.id]}</p>}
                        </div>
                        <button type="button" className="btn btn-outline-primary btn-sm"
                          disabled={pendingFiles.includes(attachment.id)} onClick={() => { void download(attachment) }}
                          aria-label={`Download ${attachment.originalFilename}`}>
                          {pendingFiles.includes(attachment.id) ? 'Downloading...' : 'Download file'}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
              <section className="surface-card request-detail-section" aria-labelledby="request-history-title">
                <h2 className="h5 mb-4" id="request-history-title">Status history</h2>
                {details.history.length === 0 ? <p className="text-muted mb-0">No history events have been recorded.</p> : (
                  <ol className="request-history-list">
                    {details.history.map((entry) => (
                      <li className="request-history-item" key={entry.id}>
                        <span className="request-history-marker" aria-hidden="true" />
                        <div className="min-width-0">
                          <h3 className="h6 mb-1 request-wrap">{entry.previousStatus ? `${REQUEST_STATUS_LABELS[entry.previousStatus]} to ${REQUEST_STATUS_LABELS[entry.newStatus]}` : REQUEST_STATUS_LABELS[entry.newStatus]}</h3>
                          <p className="small text-muted mb-1">{formatRequestTime(entry.createdAt)} · {entry.changedBy.fullName} ({ROLE_LABELS[entry.changedBy.role]})</p>
                          {entry.note && <p className="request-wrap mb-0">{entry.note}</p>}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            </div>
            <aside className="request-detail-aside" aria-label="Request identification">
              <section className="surface-card request-aside-card">
                <p className="section-eyebrow mb-2">For your records</p>
                <h2 className="h5 request-wrap">{details.requestNumber}</h2>
                <p className="small text-muted mb-3">Use this number when contacting the university about your request.</p>
                <dl className="request-mini-facts">
                  <div><dt>Student</dt><dd>{details.student.fullName}</dd></div>
                  <div><dt>Registration</dt><dd>{details.student.registrationNumber || 'Not provided'}</dd></div>
                  <div><dt>Request version</dt><dd>{details.version}</dd></div>
                </dl>
                <p className="request-readonly-note">Submitted requests cannot be edited or cancelled.</p>
              </section>
              <Link className="btn btn-outline-primary request-aside-link" to="/catalogue">Browse more documents</Link>
            </aside>
          </div>
        </>
      )}
    </>
  )
}
