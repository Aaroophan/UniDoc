import type { RequestStatus } from '../api/contracts'

export type StatusTone = 'neutral' | 'information' | 'warning' | 'success' | 'danger'
export type StatusPresentation = Readonly<{ label: string; explanation: string; tone: StatusTone; symbol: string }>

/** Single text-first status vocabulary, shared by every role and feature. */
export const STATUS_PRESENTATIONS: Record<RequestStatus, StatusPresentation> = {
  SUBMITTED: { label: 'Submitted', explanation: 'The request has been received and is waiting for staff review.', tone: 'neutral', symbol: '○' },
  UNDER_REVIEW: { label: 'Under review', explanation: 'University staff are reviewing the request.', tone: 'information', symbol: '◷' },
  ADDITIONAL_INFORMATION_REQUIRED: { label: 'Additional information required', explanation: 'The student needs to provide additional details.', tone: 'warning', symbol: '!' },
  APPROVED: { label: 'Approved', explanation: 'The request is approved and waiting to be processed.', tone: 'success', symbol: '✓' },
  REJECTED: { label: 'Rejected', explanation: 'The request was rejected and cannot be continued.', tone: 'danger', symbol: '×' },
  PROCESSING: { label: 'Processing', explanation: 'Staff are preparing the requested document.', tone: 'information', symbol: '◷' },
  READY: { label: 'Ready', explanation: 'The document is ready for its selected delivery method.', tone: 'success', symbol: '✓' },
  COMPLETED: { label: 'Completed', explanation: 'The document-request process is complete.', tone: 'neutral', symbol: '✓' },
}

/** Unexpected future API values stay visible rather than being mislabeled. */
export function presentRequestStatus(status: string): StatusPresentation {
  if (Object.prototype.hasOwnProperty.call(STATUS_PRESENTATIONS, status)) {
    return STATUS_PRESENTATIONS[status as RequestStatus]
  }
  return { label: 'Unknown status', explanation: 'This request has an unrecognized status. Refresh or contact an administrator.', tone: 'warning', symbol: '?' }
}
