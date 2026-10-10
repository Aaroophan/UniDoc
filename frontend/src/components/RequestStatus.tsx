import { REQUEST_STATUS_LABELS, isRequestStatus } from '../api/contracts'
import type { RequestStatus as Status } from '../api/contracts'

const tone: Record<Status, string> = {
  SUBMITTED: 'muted',
  UNDER_REVIEW: 'information',
  ADDITIONAL_INFORMATION_REQUIRED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  PROCESSING: 'information',
  READY: 'success',
  COMPLETED: 'muted',
}

const descriptions: Record<Status, string> = {
  SUBMITTED: 'The request has been received and is waiting for staff review.',
  UNDER_REVIEW: 'University staff are reviewing the request.',
  ADDITIONAL_INFORMATION_REQUIRED: 'The student needs to provide additional details.',
  APPROVED: 'The request is approved and waiting to be processed.',
  REJECTED: 'The request was rejected and cannot be continued.',
  PROCESSING: 'Staff are preparing the requested document.',
  READY: 'The document is ready for download or collection, depending on the delivery method.',
  COMPLETED: 'The document-request process is complete.',
}

export function RequestStatus({ status, withDescription = false }: { status: Status; withDescription?: boolean }) {
  // Runtime guard: older/newer API values should be visible, never mislabeled.
  if (!isRequestStatus(status)) return <span className="request-status">Unknown status</span>
  return (
    <span className="d-inline-flex flex-column align-items-start gap-1">
      <span className={`request-status request-status-${tone[status]}`}>
        <span className="request-status-dot" aria-hidden="true" />
        {REQUEST_STATUS_LABELS[status]}
      </span>
      {withDescription && <span className="small text-muted">{descriptions[status]}</span>}
    </span>
  )
}
