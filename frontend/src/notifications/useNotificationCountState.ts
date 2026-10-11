import { useCallback, useEffect, useState } from 'react'
import { getUnreadNotificationCount } from './notificationsApi'

/** Separate count from the list so every role's shell can display it. */
export function useNotificationCountState(userId: number | null) {
  const [unreadCount, setUnreadCount] = useState<number | null>(null)

  const refreshUnreadCount = useCallback(async () => {
    if (userId === null) return
    try {
      const result = await getUnreadNotificationCount()
      setUnreadCount(Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : null)
    } catch {
      // Layout is noncritical: keep last known value; the page shows detailed errors.
    }
  }, [userId])

  useEffect(() => {
    if (userId === null) return
    const controller = new AbortController()
    async function load() {
      try {
        const result = await getUnreadNotificationCount(controller.signal)
        if (!controller.signal.aborted) {
          setUnreadCount(Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : null)
        }
      } catch { /* Keep navigation usable when the notification endpoint is down. */ }
    }
    setUnreadCount(null)
    void load()
    const timer = window.setInterval(() => void load(), 60000)
    return () => { controller.abort(); window.clearInterval(timer) }
  }, [userId])

  return { unreadCount, refreshUnreadCount }
}
