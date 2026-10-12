import { apiClient } from '../api/client'
import { saveAuthenticatedDownload } from '../api/saveDownload'
import type { DeliveryMethod, PageResponse, RequestStatus } from '../api/contracts'
import type {
  StudentIssuedDocument, StudentRequestAction, StudentRequestAttachment, StudentRequestHistory,
} from '../requests/requestsApi'
import { requirePositiveId } from '../requests/requestsApi'
import { staffFilterParameters } from './filterParameters'

const STAFF_PATH = '/api/staff/requests'

export type StaffRequestSort = 'createdAt' | 'updatedAt' | 'requiredDate' | 'status' | 'requestNumber'

export interface StaffRequestFilters {
  q?: string
  status?: RequestStatus | ''
  documentTypeId?: number
  deliveryMethod?: DeliveryMethod | ''
  submittedFrom?: string
  submittedTo?: string
  requiredDate?: string
  sort?: StaffRequestSort
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface StaffRequestSummary {
  id: number
  requestNumber: string
  studentName: string
  registrationNumber: string | null
  documentTypeId: number
  documentTypeName: string
  deliveryMethod: DeliveryMethod
  status: RequestStatus
  requiredDate: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface StaffRequestStudent {
  id: number
  fullName: string
  email: string
  registrationNumber: string | null
  phone: string | null
  faculty: string | null
  department: string | null
  degreeProgramme: string | null
  academicYear: number | null
}

/** Mirrors StaffRequestDetailResponse.java. Never contains any stored file path or credentials. */
export interface StaffRequestDetail {
  id: number
  requestNumber: string
  student: StaffRequestStudent
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

export async function listStaffRequests(filters: StaffRequestFilters, signal?: AbortSignal): Promise<PageResponse<StaffRequestSummary>> {
  return (await apiClient.get<PageResponse<StaffRequestSummary>>(STAFF_PATH,
    { params: staffFilterParameters(filters), signal })).data
}

export async function getStaffRequest(id: number, signal?: AbortSignal): Promise<StaffRequestDetail> {
  return (await apiClient.get<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}`, { signal })).data
}

export async function startStaffReview(id: number, expectedVersion: number): Promise<StaffRequestDetail> {
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) throw new Error('Invalid request version.')
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/start-review`,
    { expectedVersion })).data
}

function checkedVersion(version: number): number {
  if (!Number.isSafeInteger(version) || version < 0) throw new Error('Invalid request version.')
  return version
}

export async function requestStaffInformation(id: number, expectedVersion: number, message: string): Promise<StaffRequestDetail> {
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/request-information`,
    { expectedVersion: checkedVersion(expectedVersion), message })).data
}

export async function approveStaffRequest(id: number, expectedVersion: number): Promise<StaffRequestDetail> {
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/approve`,
    { expectedVersion: checkedVersion(expectedVersion) })).data
}

export async function rejectStaffRequest(id: number, expectedVersion: number, reason: string): Promise<StaffRequestDetail> {
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/reject`,
    { expectedVersion: checkedVersion(expectedVersion), reason })).data
}

export async function startStaffProcessing(id: number, expectedVersion: number): Promise<StaffRequestDetail> {
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/start-processing`,
    { expectedVersion: checkedVersion(expectedVersion) })).data
}

/** PHYSICAL PROCESSING -> READY; the server retains the submitted-time collection snapshots. */
export async function markReadyForCollection(id: number, expectedVersion: number): Promise<StaffRequestDetail> {
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/ready-for-collection`,
    { expectedVersion: checkedVersion(expectedVersion) })).data
}

/** PHYSICAL READY -> COMPLETED; date is yyyy-MM-dd in the laptop's local calendar. */
export async function completePhysicalCollection(
  id: number, expectedVersion: number, collectionDate: string, note: string,
): Promise<StaffRequestDetail> {
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/complete-collection`,
    { expectedVersion: checkedVersion(expectedVersion), collectionDate, note: note.trim() || null })).data
}

/** Exactly one PDF and a text version part; browser owns the multipart boundary. */
export async function uploadIssuedDocument(id: number, expectedVersion: number, file: File): Promise<StaffRequestDetail> {
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) throw new Error('Invalid request version.')
  const form = new FormData()
  form.append('expectedVersion', String(expectedVersion))
  form.append('file', file, file.name)
  return (await apiClient.post<StaffRequestDetail>(`${STAFF_PATH}/${requirePositiveId(id)}/issued-document`,
    form, { timeout: 60000 })).data
}

export async function saveStaffIssuedDocument(requestId: number, originalFilename: string): Promise<void> {
  await saveAuthenticatedDownload(`${STAFF_PATH}/${requirePositiveId(requestId)}/issued-document`, originalFilename)
}

export async function saveStaffAttachment(requestId: number, attachment: StudentRequestAttachment): Promise<void> {
  await saveAuthenticatedDownload(`${STAFF_PATH}/${requirePositiveId(requestId)}/attachments/${requirePositiveId(attachment.id)}`,
    attachment.originalFilename || 'supporting-document')
}
