import { apiClient } from '../api/client'
import type { NotificationType, PageResponse } from '../api/contracts'

const PATH = '/api/notifications'

export interface NotificationItem {
  id: number
  type: NotificationType
  title: string
  message: string
  actionPath: string | null
  read: boolean
  readAt: string | null
  createdAt: string
}

export interface NotificationFilters {
  read?: boolean
  type?: NotificationType
  page?: number
  size?: number
}

export interface UnreadNotificationCount { count: number }

function validId(id: number): number {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Invalid notification ID.')
  return id
}

export async function listNotifications(filters: NotificationFilters = {}, signal?: AbortSignal): Promise<PageResponse<NotificationItem>> {
  const params = new URLSearchParams()
  if (typeof filters.read === 'boolean') params.set('read', String(filters.read))
  if (filters.type) params.set('type', filters.type)
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  return (await apiClient.get<PageResponse<NotificationItem>>(PATH, { params, signal })).data
}

export async function getUnreadNotificationCount(signal?: AbortSignal): Promise<UnreadNotificationCount> {
  return (await apiClient.get<UnreadNotificationCount>(`${PATH}/unread-count`, { signal })).data
}

export async function markNotificationRead(id: number): Promise<NotificationItem> {
  return (await apiClient.post<NotificationItem>(`${PATH}/${validId(id)}/read`)).data
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post(`${PATH}/read-all`)
}

/** Never navigate to a server-supplied external URL from a notification. */
export function safeNotificationPath(path: string | null): string | null {
  if (!path || !/^\/(?!\/)[a-zA-Z0-9/_?=&%.\-]*$/.test(path)) return null
  try {
    const decoded = decodeURIComponent(path)
    if (decoded.startsWith('//') || decoded.includes('\\') || /[\x00-\x1f\x7f]/.test(decoded)) return null
    if (decoded.split('?')[0].split('/').some((part) => part === '..' || part === '.')) return null
    return path
  } catch { return null }
}
