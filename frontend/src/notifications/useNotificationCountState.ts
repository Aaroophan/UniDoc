import { useCallback, useEffect, useState } from 'react'
import { getUnreadNotificationCount } from './notificationsApi'

/** Separate count from the list so every role's shell can display it. */
export function useNotificationCountState(userId: number | null) {
  // Associate the fetched value with its account: a different signed-in user
  // must never see a stale count while their first fetch is in flight.
  const [countState, setCountState] = useState<{ userId: number; count: number | null } | null>(null)
  const unreadCount = countState?.userId === userId ? countState.count : null

  const refreshUnreadCount = useCallback(async () => {
    if (userId === null) return
    try {
      const result = await getUnreadNotificationCount()
      setCountState({ userId: userId!, count: Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : null })
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
          setCountState({ userId: userId!, count: Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : null })
        }
      } catch { /* Keep navigation usable when the notification endpoint is down. */ }
    }
    void load()
    const timer = window.setInterval(() => void load(), 60000)
    return () => { controller.abort(); window.clearInterval(timer) }
  }, [userId])

  return { unreadCount, refreshUnreadCount }
}
