import { apiClient } from '../api/client'
import { saveAuthenticatedDownload } from '../api/saveDownload'
import type { DeliveryMethod, PageResponse, RequestStatus } from '../api/contracts'
import type { StaffRequestDetail, StaffRequestFilters, StaffRequestSummary } from '../staff/staffRequestsApi'
import { staffFilterParameters } from '../staff/filterParameters'
import { reportFilterParameters } from './reportFilters'
import { requirePositiveId } from '../requests/requestsApi'

const MONITORING_PATH = '/api/admin/requests'
const REPORT_PATH = '/api/admin/reports/requests'

/** ADMIN details contain only metadata; the server guarantees zero available staff actions. */
export type AdminRequestDetail = Omit<StaffRequestDetail, 'availableActions'> & { availableActions: [] }

export interface DashboardTypeCount {
  documentTypeId: number
  documentTypeName: string
  count: number
}

export interface AdminDashboard {
  totalRequests: number
  pendingRequests: number
  approvedRequests: number
  processingRequests: number
  rejectedRequests: number
  readyRequests: number
  completedRequests: number
  byDeliveryMethod: Record<DeliveryMethod, number>
  byDocumentType: DashboardTypeCount[]
  recentRequests: StaffRequestSummary[]
}

export type RequestReportSort = 'createdAt' | 'updatedAt' | 'completedAt' | 'requiredDate' | 'requestNumber' | 'status' | 'deliveryMethod'

/** Only applied filters reach both JSON and CSV endpoints. */
export interface RequestReportFilters {
  submittedFrom?: string
  submittedTo?: string
  status?: RequestStatus | ''
  documentTypeId?: number
  deliveryMethod?: DeliveryMethod | ''
  sort?: RequestReportSort
  direction?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface RequestReportRow {
  requestNumber: string
  registrationNumber: string | null
  studentName: string
  studentEmail: string
  documentTypeName: string
  purpose: string
  deliveryMethod: DeliveryMethod
  requiredDate: string
  status: RequestStatus
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export async function listAdminRequests(filters: StaffRequestFilters, signal?: AbortSignal): Promise<PageResponse<StaffRequestSummary>> {
  return (await apiClient.get<PageResponse<StaffRequestSummary>>(MONITORING_PATH,
    { params: staffFilterParameters(filters), signal })).data
}

export async function getAdminRequest(id: number, signal?: AbortSignal): Promise<AdminRequestDetail> {
  return (await apiClient.get<AdminRequestDetail>(`${MONITORING_PATH}/${requirePositiveId(id)}`, { signal })).data
}

export async function getAdminDashboard(signal?: AbortSignal): Promise<AdminDashboard> {
  return (await apiClient.get<AdminDashboard>('/api/admin/dashboard', { signal })).data
}

export async function listRequestReport(filters: RequestReportFilters, signal?: AbortSignal): Promise<PageResponse<RequestReportRow>> {
  return (await apiClient.get<PageResponse<RequestReportRow>>(REPORT_PATH,
    { params: reportFilterParameters(filters), signal })).data
}

export async function saveRequestReportCsv(filters: RequestReportFilters): Promise<void> {
  const params = reportFilterParameters(filters, true)
  const query = params.toString()
  await saveAuthenticatedDownload(`${REPORT_PATH}.csv${query ? `?${query}` : ''}`, 'unidoc-request-report.csv')
}
