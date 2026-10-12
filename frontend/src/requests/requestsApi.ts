import { apiClient } from '../api/client'
import { saveAuthenticatedDownload } from '../api/saveDownload'
import type { DeliveryMethod, PageResponse, RequestStatus, UserRole } from '../api/contracts'

const REQUESTS_PATH = '/api/student/requests'

export type StudentRequestAction =
  | 'START_REVIEW' | 'REQUEST_INFORMATION' | 'APPROVE' | 'REJECT'
  | 'RESPOND' | 'START_PROCESSING' | 'UPLOAD_ISSUED_DOCUMENT'
  | 'READY_FOR_COLLECTION' | 'COMPLETE_COLLECTION' | 'DOWNLOAD_ISSUED_DOCUMENT'

export interface StudentRequestSummary {
  id: number
  requestNumber: string
  documentTypeId: number
  documentTypeName: string
  deliveryMethod: DeliveryMethod
  status: RequestStatus
  requiredDate: string
  createdAt: string
  updatedAt: string
  version: number
}

export type StudentRequestAttachmentKind = 'INITIAL_SUPPORT' | 'ADDITIONAL_INFORMATION'

export interface StudentRequestAttachment {
  id: number
  kind: StudentRequestAttachmentKind
  originalFilename: string
  contentType: string
  sizeBytes: number
  createdAt: string
}

export interface StudentRequestActor {
  id: number
  fullName: string
  role: UserRole
}

export interface StudentRequestHistory {
  id: number
  previousStatus: RequestStatus | null
  newStatus: RequestStatus
  changedBy: StudentRequestActor
  note: string | null
  createdAt: string
}

export interface StudentRequestStudent {
  id: number
  fullName: string
  email: string
  registrationNumber: string | null
}

export interface StudentIssuedDocument {
  originalFilename: string
  sizeBytes: number
  downloadCount: number
  firstDownloadedAt: string | null
  createdAt: string
}

/** Mirrors StudentRequestDetailResponse.java; never includes private storage paths. */
export interface StudentRequestDetail {
  id: number
  requestNumber: string
  student: StudentRequestStudent
  documentTypeId: number
  documentTypeName: string
  purpose: string
  requiredDate: string
  deliveryMethod: DeliveryMethod
  status: RequestStatus
  additionalInformationMessage: string | null
  rejectionReason: string | null
  collectionLocation: string | null
  collectionInstructions: string | null
  readyAt: string | null
  completedAt: string | null
  collectionDate: string | null
  collectionNote: string | null
  attachments: StudentRequestAttachment[]
  issuedDocument: StudentIssuedDocument | null
  history: StudentRequestHistory[]
  availableActions: StudentRequestAction[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface CreateStudentRequest {
  documentTypeId: number
  purpose: string
  requiredDate: string
  deliveryMethod: DeliveryMethod
}

export type StudentRequestSort = 'createdAt' | 'updatedAt' | 'requiredDate' | 'status' | 'requestNumber'

export interface StudentRequestFilters {
  status?: RequestStatus | ''
  deliveryMethod?: DeliveryMethod | ''
  page?: number
  size?: number
  sort?: StudentRequestSort
  direction?: 'asc' | 'desc'
}

export class SubmissionOutcomeUnconfirmedError extends Error {
  constructor() { super('Request creation response was not confirmed.') }
}

export function requirePositiveId(id: number): number {
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('Invalid request identifier.')
  return id
}

/** Route query/path parameters are untrusted, even behind an authenticated route. */
export function parsePositiveId(raw: string | null | undefined): number | null {
  if (!raw || !/^[1-9][0-9]*$/.test(raw)) return null
  const id = Number(raw)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

export async function createStudentRequest(command: CreateStudentRequest, files: readonly File[]): Promise<StudentRequestDetail> {
  const form = new FormData()
  form.append('request', new Blob([JSON.stringify(command)], { type: 'application/json' }))
  for (const file of files) form.append('files', file, file.name)
  // Axios/browser sets Content-Type with the correct multipart boundary. Never set it manually.
  const response = await apiClient.post<StudentRequestDetail>(REQUESTS_PATH, form, { timeout: 60000 })
  if (response.status !== 201) throw new SubmissionOutcomeUnconfirmedError()
  return response.data
}

export async function listStudentRequests(filters: StudentRequestFilters = {}, signal?: AbortSignal): Promise<PageResponse<StudentRequestSummary>> {
  const params = new URLSearchParams()
  if (filters.status?.trim()) params.set('status', filters.status.trim())
  if (filters.deliveryMethod?.trim()) params.set('deliveryMethod', filters.deliveryMethod.trim())
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  if (filters.sort) params.set('sort', filters.sort)
  if (filters.direction) params.set('direction', filters.direction)
  return (await apiClient.get<PageResponse<StudentRequestSummary>>(REQUESTS_PATH, { params, signal })).data
}

export async function getStudentRequest(id: number, signal?: AbortSignal): Promise<StudentRequestDetail> {
  return (await apiClient.get<StudentRequestDetail>(`${REQUESTS_PATH}/${requirePositiveId(id)}`, { signal })).data
}

/**
 * The only browser download path: owner-scoped API -> authenticated Blob -> save -> revoke.
 * The page never sees private paths, public file URLs, or object-URL lifecycle details.
 */
export async function saveStudentAttachment(requestId: number, attachment: StudentRequestAttachment): Promise<void> {
  return saveAuthenticatedDownload(
    `${REQUESTS_PATH}/${requirePositiveId(requestId)}/attachments/${requirePositiveId(attachment.id)}`,
    attachment.originalFilename || 'supporting-document',
  )
}
