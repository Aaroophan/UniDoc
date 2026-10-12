import type { StaffRequestFilters } from './staffRequestsApi'

/** Only documented filters may leave the client. Search text is URL-encoded. */
export function staffFilterParameters(filters: StaffRequestFilters): URLSearchParams {
  if (filters.documentTypeId !== undefined &&
      (!Number.isSafeInteger(filters.documentTypeId) || filters.documentTypeId < 1)) {
    throw new Error('Invalid document type identifier.')
  }
  const params = new URLSearchParams()
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  if (filters.status) params.set('status', filters.status)
  if (filters.documentTypeId !== undefined) params.set('documentTypeId', String(filters.documentTypeId))
  if (filters.deliveryMethod) params.set('deliveryMethod', filters.deliveryMethod)
  if (filters.submittedFrom) params.set('submittedFrom', filters.submittedFrom)
  if (filters.submittedTo) params.set('submittedTo', filters.submittedTo)
  if (filters.requiredDate) params.set('requiredDate', filters.requiredDate)
  if (filters.sort) params.set('sort', filters.sort)
  if (filters.direction) params.set('direction', filters.direction)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  return params
}

