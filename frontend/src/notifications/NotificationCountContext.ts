import { createContext, useContext } from 'react'

export interface NotificationCountContextValue {
  unreadCount: number | null
  refreshUnreadCount: () => Promise<void>
}

export const NotificationCountContext = createContext<NotificationCountContextValue | null>(null)

export function useNotificationCount(): NotificationCountContextValue {
  const context = useContext(NotificationCountContext)
  if (!context) throw new Error('Notification count requires the authenticated application layout.')
  return context
}
