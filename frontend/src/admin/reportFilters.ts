import { DELIVERY_METHODS, REQUEST_STATUSES } from '../api/contracts.ts'
import type { DeliveryMethod, RequestStatus } from '../api/contracts.ts'
import type { RequestReportFilters, RequestReportSort } from './adminInsightsApi'

export const REPORT_SORTS: RequestReportSort[] = [
  'createdAt', 'updatedAt', 'completedAt', 'requiredDate', 'requestNumber', 'status', 'deliveryMethod',
]

export function reportPage(value: string | null): number {
  if (!value || !/^(0|[1-9][0-9]*)$/.test(value)) return 0
  const number = Number(value)
  return Number.isSafeInteger(number) && number <= 1000000 ? number : 0
}

export function readReportFilters(params: URLSearchParams): RequestReportFilters {
  const status = params.get('status') ?? ''
  const method = params.get('deliveryMethod') ?? ''
  const typeId = params.get('documentTypeId') ?? ''
  const sort = params.get('sort') ?? ''
  const direction = params.get('direction') ?? ''
  const number = /^[1-9]\d*$/.test(typeId) ? Number(typeId) : undefined
  return {
    submittedFrom: params.get('submittedFrom') || undefined,
    submittedTo: params.get('submittedTo') || undefined,
    status: REQUEST_STATUSES.includes(status as RequestStatus) ? status as RequestStatus : undefined,
    documentTypeId: number && Number.isSafeInteger(number) ? number : undefined,
    deliveryMethod: DELIVERY_METHODS.includes(method as DeliveryMethod) ? method as DeliveryMethod : undefined,
    sort: REPORT_SORTS.includes(sort as RequestReportSort) ? sort as RequestReportSort : 'createdAt',
    direction: direction === 'asc' ? 'asc' : 'desc',
    page: reportPage(params.get('page')),
    size: 10,
  }
}

export function appliedReportUrl(filters: RequestReportFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.submittedFrom) params.set('submittedFrom', filters.submittedFrom)
  if (filters.submittedTo) params.set('submittedTo', filters.submittedTo)
  if (filters.status) params.set('status', filters.status)
  if (filters.documentTypeId) params.set('documentTypeId', String(filters.documentTypeId))
  if (filters.deliveryMethod) params.set('deliveryMethod', filters.deliveryMethod)
  if (filters.sort && filters.sort !== 'createdAt') params.set('sort', filters.sort)
  if (filters.direction && filters.direction !== 'desc') params.set('direction', filters.direction)
  if (filters.page && filters.page > 0) params.set('page', String(filters.page))
  return params
}

export function reportFilterParameters(filters: RequestReportFilters, csv = false): URLSearchParams {
  if (filters.documentTypeId !== undefined &&
      (!Number.isSafeInteger(filters.documentTypeId) || filters.documentTypeId < 1)) {
    throw new Error('Invalid document type identifier.')
  }
  const result = new URLSearchParams()
  if (filters.submittedFrom) result.set('submittedFrom', filters.submittedFrom)
  if (filters.submittedTo) result.set('submittedTo', filters.submittedTo)
  if (filters.status) result.set('status', filters.status)
  if (filters.documentTypeId !== undefined) result.set('documentTypeId', String(filters.documentTypeId))
  if (filters.deliveryMethod) result.set('deliveryMethod', filters.deliveryMethod)
  if (filters.sort) result.set('sort', filters.sort)
  if (filters.direction) result.set('direction', filters.direction)
  if (!csv) {
    if (filters.page !== undefined) result.set('page', String(filters.page))
    if (filters.size !== undefined) result.set('size', String(filters.size))
  }
  return result
}

