import type { ReactNode } from 'react'

type PageStateProps =
  | { status: 'loading'; message?: string }
  | { status: 'empty'; title: string; message: string; action?: ReactNode }
  | { status: 'error'; title?: string; message: string; requestId?: string | null; onRetry?: () => void }
  | { status: 'data'; children: ReactNode }

export function PageState(props: PageStateProps) {
  if (props.status === 'data') return <>{props.children}</>

  if (props.status === 'loading') {
    return (
      <div className="page-state" role="status" aria-live="polite">
        <div className="spinner-border text-brand" aria-hidden="true" />
        <p className="mb-0">{props.message ?? 'Loading your information...'}</p>
      </div>
    )
  }

  if (props.status === 'empty') {
    return (
      <div className="page-state" role="status">
        <div className="state-symbol state-empty" aria-hidden="true">–</div>
        <h2 className="h5 mb-2">{props.title}</h2>
        <p className="text-muted mb-3">{props.message}</p>
        {props.action}
      </div>
    )
  }

  return (
    <div className="page-state" role="alert">
      <div className="state-symbol state-error" aria-hidden="true">!</div>
      <h2 className="h5 mb-2">{props.title ?? 'Unable to load this page'}</h2>
      <p className="text-muted mb-2">{props.message}</p>
      {props.requestId && <p className="small text-muted mb-3">Request ID: <code>{props.requestId}</code></p>}
      {props.onRetry && <button type="button" className="btn btn-outline-primary" onClick={props.onRetry}>Try again</button>}
    </div>
  )
}
