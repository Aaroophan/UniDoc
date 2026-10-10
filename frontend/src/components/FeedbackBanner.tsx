export type FeedbackVariant = 'success' | 'warning' | 'error' | 'info'

export interface FeedbackBannerProps {
  variant: FeedbackVariant
  message: string
  title?: string
  requestId?: string | null
  onDismiss?: () => void
}

const variantLabels: Record<FeedbackVariant, string> = {
  success: 'Success', warning: 'Attention', error: 'Error', info: 'Information',
}

export function FeedbackBanner({ variant, message, title, requestId, onDismiss }: FeedbackBannerProps) {
  return (
    <div
      className={`feedback-banner feedback-${variant} d-flex align-items-start gap-3`}
      role={variant === 'error' || variant === 'warning' ? 'alert' : 'status'}
    >
      <span className="feedback-dot" aria-hidden="true" />
      <div className="flex-grow-1 min-width-0">
        <strong className="d-block mb-1">{title ?? variantLabels[variant]}</strong>
        <div>{message}</div>
        {requestId && <div className="small opacity-75 mt-1">Request ID: <code>{requestId}</code></div>}
      </div>
      {onDismiss && (
        <button type="button" className="feedback-dismiss" aria-label="Dismiss message" onClick={onDismiss}>×</button>
      )}
    </div>
  )
}
