import axios from 'axios'
import { useCallback, useRef, useState } from 'react'
import type { Dispatch, SetStateAction } from 'react'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import type { StudentRequestAction } from '../requests/requestsApi'
import { getStaffRequest } from './staffRequestsApi'
import type { StaffRequestDetail } from './staffRequestsApi'

type Alert = { variant: 'error' | 'warning' | 'success'; message: string; requestId?: string | null }
type Command = (requestId: number, version: number) => Promise<StaffRequestDetail>

/** Future staff commands share this version-aware no-auto-retry execution boundary. */
export function useStaffRequestAction(requestId: number | null, setDetails: Dispatch<SetStateAction<StaffRequestDetail | null>>) {
  const [busyAction, setBusyAction] = useState<StudentRequestAction | null>(null)
  const [alert, setAlert] = useState<Alert | null>(null)
  const [needsRefresh, setNeedsRefresh] = useState(false)
  const acknowledgeFreshDetail = useCallback(() => setNeedsRefresh(false), [])
  const executionLock = useRef(false)

  async function reloadAfterUnconfirmed(): Promise<boolean> {
    if (requestId === null) return false
    try {
      const latest = await getStaffRequest(requestId)
      setDetails(latest)
      acknowledgeFreshDetail()
      return true
    } catch {
      setNeedsRefresh(true)
      return false
    }
  }

  async function run(action: StudentRequestAction, currentVersion: number, command: Command): Promise<boolean> {
    if (requestId === null || executionLock.current || needsRefresh) return false
    executionLock.current = true
    setBusyAction(action)
    setAlert(null)
    try {
      const updated = await command(requestId, currentVersion)
      setDetails(updated)
      acknowledgeFreshDetail()
      setAlert({ variant: 'success', message: 'Request updated successfully. The latest status and history are shown below.' })
      return true
    } catch (failure: unknown) {
      const api = getApiError(failure)
      if (api?.code === 'REQUEST_VERSION_CONFLICT' || api?.code === 'INVALID_REQUEST_TRANSITION') {
        const refreshed = await reloadAfterUnconfirmed()
        setAlert({ variant: 'warning',
          message: refreshed
            ? 'This request changed since you opened it. Latest details are loaded. Review them before choosing an action again.'
            : 'This request changed, but the latest details could not be loaded. Refresh before taking another action.',
          requestId: api?.requestId })
      } else if (axios.isAxiosError(failure) && !failure.response) {
        // A timeout or disconnect does not prove the command failed. Never retry it automatically.
        const refreshed = await reloadAfterUnconfirmed()
        setAlert({ variant: 'warning', message: refreshed
          ? 'Could not confirm whether the action completed. The request was refreshed; check its status and history before retrying.'
          : 'Could not confirm whether the action completed, and refresh failed. Check your connection, then reload before retrying.' })
      } else {
        setAlert({ variant: 'error', message: getSafeErrorMessage(failure, 'The action could not be completed.'), requestId: api?.requestId })
      }
      return false
    } finally {
      executionLock.current = false
      setBusyAction(null)
    }
  }

  return { run, busyAction, alert, needsRefresh, acknowledgeFreshDetail, clearAlert: () => setAlert(null) }
}
