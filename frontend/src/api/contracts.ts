/** Canonical backend values. Display names belong in labels, never in requests. */
export const USER_ROLES = ['STUDENT', 'STAFF', 'ADMIN'] as const
export type UserRole = (typeof USER_ROLES)[number]

export const USER_STATUSES = ['ACTIVE', 'INACTIVE'] as const
export type UserStatus = (typeof USER_STATUSES)[number]

export const DELIVERY_METHODS = ['ELECTRONIC', 'PHYSICAL'] as const
export type DeliveryMethod = (typeof DELIVERY_METHODS)[number]

export const REQUEST_STATUSES = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'ADDITIONAL_INFORMATION_REQUIRED',
  'APPROVED',
  'REJECTED',
  'PROCESSING',
  'READY',
  'COMPLETED',
] as const
export type RequestStatus = (typeof REQUEST_STATUSES)[number]

export const ATTACHMENT_KINDS = ['INITIAL_SUPPORT', 'ADDITIONAL_INFORMATION'] as const
export type AttachmentKind = (typeof ATTACHMENT_KINDS)[number]

export const NOTIFICATION_TYPES = [
  'REQUEST_SUBMITTED',
  'ADDITIONAL_INFORMATION_REQUIRED',
  'ADDITIONAL_INFORMATION_SUBMITTED',
  'REQUEST_APPROVED',
  'REQUEST_REJECTED',
  'PROCESSING_STARTED',
  'ELECTRONIC_DOCUMENT_READY',
  'PHYSICAL_DOCUMENT_READY',
  'REQUEST_COMPLETED',
] as const
export type NotificationType = (typeof NOTIFICATION_TYPES)[number]

export type FieldErrors = Record<string, string>

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  requestId: string
  fieldErrors: FieldErrors | null
}

export interface PageResponse<T> {
  items: T[]
  page: number // Zero-based, as returned by the API.
  size: number
  totalItems: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface UserSummary {
  id: number
  email: string
  fullName: string
  role: UserRole
  status: UserStatus
  createdAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserSummary
}

export interface RegisterRequest {
  fullName: string
  email: string
  password: string
  confirmPassword: string
}

export const ROLE_LABELS: Record<UserRole, string> = {
  STUDENT: 'Student',
  STAFF: 'Staff',
  ADMIN: 'Administrator',
}

export const ACCOUNT_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
}

export const DELIVERY_METHOD_LABELS: Record<DeliveryMethod, string> = {
  ELECTRONIC: 'Electronic',
  PHYSICAL: 'Physical',
}

export const REQUEST_STATUS_LABELS: Record<RequestStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  ADDITIONAL_INFORMATION_REQUIRED: 'Additional information required',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PROCESSING: 'Processing',
  READY: 'Ready',
  COMPLETED: 'Completed',
}

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  REQUEST_SUBMITTED: 'Request submitted',
  ADDITIONAL_INFORMATION_REQUIRED: 'Additional information required',
  ADDITIONAL_INFORMATION_SUBMITTED: 'Information submitted',
  REQUEST_APPROVED: 'Request approved',
  REQUEST_REJECTED: 'Request rejected',
  PROCESSING_STARTED: 'Processing started',
  ELECTRONIC_DOCUMENT_READY: 'Electronic document ready',
  PHYSICAL_DOCUMENT_READY: 'Physical document ready',
  REQUEST_COMPLETED: 'Request completed',
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function isUserRole(value: unknown): value is UserRole {
  return typeof value === 'string' && USER_ROLES.some((role) => role === value)
}

export function isUserStatus(value: unknown): value is UserStatus {
  return typeof value === 'string' && USER_STATUSES.some((status) => status === value)
}

export function isRequestStatus(value: unknown): value is RequestStatus {
  return typeof value === 'string' && REQUEST_STATUSES.some((status) => status === value)
}

export function isUserSummary(value: unknown): value is UserSummary {
  if (!isRecord(value)) return false
  const { id, email, fullName, role, status, createdAt } = value
  return (
    typeof id === 'number' && Number.isSafeInteger(id) && id > 0 &&
    typeof email === 'string' && email.trim().length > 0 && email.length <= 254 &&
    typeof fullName === 'string' && fullName.trim().length > 0 && fullName.length <= 150 &&
    isUserRole(role) && isUserStatus(status) &&
    typeof createdAt === 'string' && Number.isFinite(Date.parse(createdAt))
  )
}
