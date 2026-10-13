import type { RequestStatus as Status } from '../api/contracts'
import { presentRequestStatus } from './requestStatusPresentation'

export function RequestStatus({ status, withDescription = false }: { status: Status; withDescription?: boolean }) {
  const present = presentRequestStatus(status)
  return (
    <span className="d-inline-flex flex-column align-items-start gap-1">
      <span className={`request-status request-status-${present.tone}`}>
        <span className="request-status-icon" aria-hidden="true">{present.symbol}</span>
        {present.label}
      </span>
      {withDescription && <span className="small text-muted">{present.explanation}</span>}
    </span>
  )
}
