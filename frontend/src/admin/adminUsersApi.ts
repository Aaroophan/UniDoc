import { apiClient } from '../api/client'
import type { PageResponse, UserRole, UserStatus, UserSummary } from '../api/contracts'

const USERS_PATH = '/api/admin/users'

export interface UserFilters {
  q?: string
  role?: UserRole | ''
  status?: UserStatus | ''
  page?: number
  size?: number
  sort?: 'createdAt' | 'fullName' | 'email' | 'role' | 'status'
  direction?: 'asc' | 'desc'
}

export interface CreateManagedAccount {
  fullName: string
  email: string
  role: 'STAFF' | 'ADMIN'
  password: string
}

export interface ResetManagedPassword {
  newPassword: string
  confirmPassword: string
}

export async function listAdminUsers(filters: UserFilters = {}, signal?: AbortSignal): Promise<PageResponse<UserSummary>> {
  const params = new URLSearchParams()
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  if (filters.role) params.set('role', filters.role)
  if (filters.status) params.set('status', filters.status)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  if (filters.sort) params.set('sort', filters.sort)
  if (filters.direction) params.set('direction', filters.direction)
  const response = await apiClient.get<PageResponse<UserSummary>>(USERS_PATH, { params, signal })
  return response.data
}

export async function createManagedAccount(values: CreateManagedAccount): Promise<UserSummary> {
  const response = await apiClient.post<UserSummary>(USERS_PATH, values)
  return response.data
}

export async function changeManagedAccountStatus(id: number, status: UserStatus): Promise<UserSummary> {
  const response = await apiClient.patch<UserSummary>(`${USERS_PATH}/${id}/status`, { status })
  return response.data
}

export async function resetManagedAccountPassword(id: number, values: ResetManagedPassword): Promise<void> {
  await apiClient.post<void>(`${USERS_PATH}/${id}/reset-password`, values)
}
