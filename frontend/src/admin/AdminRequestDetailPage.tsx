import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { DELIVERY_METHOD_LABELS, REQUEST_STATUS_LABELS, ROLE_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { RequestStatus as RequestStatusBadge } from '../components/RequestStatus'
import { formatFileSize, formatRequestDate, formatRequestTime } from '../requests/requestFormat'
import { parsePositiveId } from '../requests/requestsApi'
import { getAdminRequest } from './adminInsightsApi'
import type { AdminRequestDetail } from './adminInsightsApi'
import './adminInsights.css'

export function AdminRequestDetailPage() {
  const { requestId } = useParams()
  const id = parsePositiveId(requestId)
  const location = useLocation()
  const originatingPath = location.state && typeof location.state === 'object' && 'from' in location.state
    ? location.state.from : null
  const backPath = typeof originatingPath === 'string' && /^\/admin\/requests(?:\?[^#]*)?$/.test(originatingPath)
    ? originatingPath : '/admin/requests'
  const [reload, setReload] = useState(0)
  return <AdminRequestDetailView key={`${id ?? 'invalid'}-${reload}`} id={id} backPath={backPath}
    onRefresh={() => setReload((n) => n + 1)} />
}

function AdminRequestDetailView({ id, backPath, onRefresh }: {
  id: number | null; backPath: string; onRefresh: () => void
}) {
  const [data, setData] = useState<AdminRequestDetail | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'not-found'>('loading')
  const [error, setError] = useState<{ message: string; requestId?: string | null } | null>(null)

  useEffect(() => {
    if (id === null) return
    const controller = new AbortController()
    getAdminRequest(id, controller.signal).then((details) => {
      if (!controller.signal.aborted) { setData(details); setState('ready') }
    }).catch((failure: unknown) => {
      if (controller.signal.aborted) return
      const api = getApiError(failure)
      if (api?.status === 404) setState('not-found')
      else {
        setError({ message: getSafeErrorMessage(failure, 'Unable to load request details.'), requestId: api?.requestId })
        setState('error')
      }
    })
    return () => controller.abort()
  }, [id])

  return <>
    <PageHeader eyebrow="Administration · monitoring" title={data?.requestNumber ?? 'Request details'}
      description="Read-only request, student, document metadata and permanent workflow history. Administrator monitoring does not grant file access or staff actions."
      actions={<div className="d-flex flex-wrap gap-2">
        <Link className="btn btn-outline-secondary" to={backPath}>Back to monitoring</Link>
        {id !== null && <button className="btn btn-outline-primary" type="button" onClick={onRefresh}>Refresh</button>}
      </div>} />
    {(id === null || state === 'not-found') && <PageState status="empty" title="Request not found" message="No request is available at this address."
      action={<Link to="/admin/requests" className="btn btn-primary">Return to monitoring</Link>} />}
    {id !== null && state === 'loading' && <PageState status="loading" message="Loading monitoring details..." />}
    {id !== null && state === 'error' && <PageState status="error" title="Unable to load request"
      message={error?.message ?? 'Please try again.'} requestId={error?.requestId} onRetry={onRefresh} />}
    {id !== null && state === 'ready' && data && <>
      <section className="surface-card request-status-panel" aria-labelledby="admin-status-heading">
        <div className="request-status-panel-head"><div>
          <p className="section-eyebrow mb-2">Current workflow state</p>
          <h2 id="admin-status-heading" className="h4">{REQUEST_STATUS_LABELS[data.status]}</h2>
          <p className="text-muted mb-0">Read-only monitoring · Version {data.version}</p>
        </div><RequestStatusBadge status={data.status} /></div>
        {data.additionalInformationMessage && <div className="request-attention-notice"><strong>Awaiting additional information</strong>
          <p className="request-wrap mt-2 mb-0">{data.additionalInformationMessage}</p></div>}
        {data.rejectionReason && <div className="request-rejected-notice"><strong>Rejection reason</strong>
          <p className="request-wrap mt-2 mb-0">{data.rejectionReason}</p></div>}
      </section>
      <div className="request-detail-layout mt-4">
        <div className="request-detail-primary">
          <section className="surface-card request-detail-section" aria-labelledby="admin-student-heading">
            <h2 id="admin-student-heading" className="h5 mb-4">Student and academic profile</h2>
            <dl className="request-detail-facts staff-profile-grid">
              <div><dt>Name</dt><dd className="request-wrap">{data.student.fullName}</dd></div>
              <div><dt>Registration</dt><dd>{data.student.registrationNumber || 'Not recorded'}</dd></div>
              <div><dt>Email</dt><dd className="request-wrap">{data.student.email}</dd></div>
              <div><dt>Phone</dt><dd>{data.student.phone || 'Not provided'}</dd></div>
              <div><dt>Faculty</dt><dd>{data.student.faculty || 'Not recorded'}</dd></div>
              <div><dt>Department</dt><dd>{data.student.department || 'Not recorded'}</dd></div>
              <div><dt>Degree programme</dt><dd>{data.student.degreeProgramme || 'Not recorded'}</dd></div>
              <div><dt>Academic year</dt><dd>{data.student.academicYear ?? 'Not recorded'}</dd></div>
            </dl>
          </section>
          <section className="surface-card request-detail-section" aria-labelledby="admin-summary-heading">
            <h2 id="admin-summary-heading" className="h5 mb-4">Request information</h2>
            <dl className="request-detail-facts">
              <div><dt>Document type (at submission)</dt><dd className="request-wrap">{data.documentTypeName}</dd></div>
              <div><dt>Delivery</dt><dd>{DELIVERY_METHOD_LABELS[data.deliveryMethod]}</dd></div>
              <div><dt>Required by</dt><dd>{formatRequestDate(data.requiredDate)}</dd></div>
              <div><dt>Submitted</dt><dd>{formatRequestTime(data.createdAt)}</dd></div>
              <div><dt>Updated</dt><dd>{formatRequestTime(data.updatedAt)}</dd></div>
              {data.readyAt && <div><dt>Ready at</dt><dd>{formatRequestTime(data.readyAt)}</dd></div>}
              {data.completedAt && <div><dt>Completed at</dt><dd>{formatRequestTime(data.completedAt)}</dd></div>}
            </dl>
            <div className="request-purpose-view"><h3 className="h6">Purpose</h3><p className="request-wrap mb-0">{data.purpose}</p></div>
          </section>
          {data.deliveryMethod === 'PHYSICAL' && <section className="surface-card request-detail-section" aria-labelledby="admin-collection-heading">
            <h2 id="admin-collection-heading" className="h5 mb-3">Physical collection</h2>
            <dl className="request-collection-facts">
              <div><dt>Location (at submission)</dt><dd className="request-wrap">{data.collectionLocation || 'Not specified'}</dd></div>
              <div><dt>Instructions (at submission)</dt><dd className="request-wrap">{data.collectionInstructions || 'Not specified'}</dd></div>
              <div><dt>Collection date</dt><dd>{formatRequestDate(data.collectionDate)}</dd></div>
              {data.collectionNote && <div><dt>Collection note</dt><dd className="request-wrap">{data.collectionNote}</dd></div>}
            </dl>
          </section>}
          {data.deliveryMethod === 'ELECTRONIC' && <section className="surface-card request-detail-section" aria-labelledby="admin-issued-heading">
            <h2 id="admin-issued-heading" className="h5 mb-3">Issued document metadata</h2>
            {data.issuedDocument ? <dl className="request-collection-facts">
              <div><dt>Filename</dt><dd className="request-wrap">{data.issuedDocument.originalFilename}</dd></div>
              <div><dt>Size</dt><dd>{formatFileSize(data.issuedDocument.sizeBytes)}</dd></div>
              <div><dt>Created</dt><dd>{formatRequestTime(data.issuedDocument.createdAt)}</dd></div>
              <div><dt>Student downloads</dt><dd>{data.issuedDocument.downloadCount}</dd></div>
              {data.issuedDocument.firstDownloadedAt && <div><dt>First download</dt><dd>{formatRequestTime(data.issuedDocument.firstDownloadedAt)}</dd></div>}
            </dl> : <p className="text-muted mb-0">No electronic document issued yet.</p>}
          </section>}
          <section className="surface-card request-detail-section" aria-labelledby="admin-attachments-heading">
            <h2 id="admin-attachments-heading" className="h5 mb-2">Attachment metadata</h2>
            <p className="small text-muted">Names, types and sizes only. Administrators cannot download private attachments.</p>
            {data.attachments.length === 0 ? <p className="text-muted mb-0">No attachments.</p> :
              <ul className="admin-attachment-list">{data.attachments.map((file) => <li className="admin-breakdown-row" key={file.id}>
                <span className="request-wrap">{file.originalFilename}<small className="d-block text-muted">{file.kind === 'INITIAL_SUPPORT' ? 'Initial supporting file' : 'Additional information'} · {formatRequestTime(file.createdAt)}</small></span>
                <span>{formatFileSize(file.sizeBytes)}</span>
              </li>)}</ul>}
          </section>
        </div>
        <div className="request-detail-aside">
          <section className="surface-card request-aside-card" aria-labelledby="admin-history-heading">
            <h2 id="admin-history-heading" className="h5 mb-4">Request history</h2>
            {data.history.length === 0 ? <p className="text-muted mb-0">No history yet.</p> :
              <ol className="request-history-list">{data.history.map((event) => <li className="request-history-item" key={event.id}>
                <span className="request-history-marker" aria-hidden="true" />
                <h3 className="h6 mb-1">{REQUEST_STATUS_LABELS[event.newStatus]}</h3>
                <p className="small text-muted mb-1">{formatRequestTime(event.createdAt)}</p>
                <p className="small text-muted mb-0 request-wrap">By {event.changedBy.fullName} ({ROLE_LABELS[event.changedBy.role]})</p>
                {event.previousStatus && <p className="small text-muted mb-0">From {REQUEST_STATUS_LABELS[event.previousStatus]}</p>}
                {event.note && <p className="request-wrap mb-0 mt-2">{event.note}</p>}
              </li>)}</ol>}
          </section>
          <section className="surface-card request-aside-card">
            <h2 className="h6">Monitoring permissions</h2>
            <p className="small text-muted mb-0">Administrators can inspect status and metadata but cannot approve, reject, complete, upload, or download documents.</p>
          </section>
        </div>
      </div>
    </>}
  </>
}
