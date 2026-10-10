import type { AxiosRequestConfig } from 'axios'
import { apiClient } from '../api/client'
import type { LoginRequest, LoginResponse, RegisterRequest, UserSummary } from '../api/contracts'

export async function loginUser(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/auth/login', request)
  return data
}

export async function registerStudent(request: RegisterRequest): Promise<UserSummary> {
  const { data } = await apiClient.post<UserSummary>('/api/auth/register', request)
  return data
}

export async function getCurrentUser(config?: Pick<AxiosRequestConfig, 'signal'>): Promise<UserSummary> {
  const { data } = await apiClient.get<UserSummary>('/api/auth/me', config)
  return data
}
