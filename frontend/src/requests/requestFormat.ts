import type { DeliveryMethod, RequestStatus } from '../api/contracts'

const fullDate = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' })
const fullTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function formatRequestDate(value: string | null | undefined): string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return 'Not provided'
  const date = new Date(`${value}T00:00:00Z`)
  return Number.isNaN(date.getTime()) ? 'Not provided' : fullDate.format(date)
}

export function formatRequestTime(value: string | null | undefined): string {
  if (!value) return 'Not yet'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Not available' : fullTime.format(date)
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return 'Unknown size'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`
}

export function statusExplanation(status: RequestStatus, method: DeliveryMethod): string {
  if (status === 'READY' && method === 'PHYSICAL') return 'Your document is ready for collection at the location shown below.'
  if (status === 'READY' && method === 'ELECTRONIC') return 'Your issued PDF is ready to download. The first successful download completes this request.'
  if (status === 'COMPLETED' && method === 'PHYSICAL') return 'The university recorded collection of the physical document. This request is complete.'
  if (status === 'COMPLETED') return 'The electronic document request has been completed.'
  if (status === 'ADDITIONAL_INFORMATION_REQUIRED') return 'Staff need additional information before they can continue reviewing this request.'
  if (status === 'REJECTED') return 'Staff rejected this request. Its history and supporting files remain available to review.'
  if (status === 'SUBMITTED') return 'Your request has been received and is waiting for university staff to review it.'
  if (status === 'UNDER_REVIEW') return 'University staff are reviewing your request.'
  if (status === 'APPROVED') return 'Staff have approved your request; document preparation has not yet started.'
  return 'University staff are preparing your document.'
}
