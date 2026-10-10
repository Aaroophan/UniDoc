import axios from 'axios'
import type { ApiErrorResponse, FieldErrors } from './contracts'
import { isRecord } from './contracts'

function isFieldErrors(value: unknown): value is FieldErrors {
  return isRecord(value) && Object.values(value).every((message) => typeof message === 'string')
}

/** Only server data conforming to the documented error envelope is displayed. */
export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!isRecord(value)) return false
  return (
    typeof value.timestamp === 'string' && Number.isFinite(Date.parse(value.timestamp)) &&
    typeof value.status === 'number' && Number.isInteger(value.status) &&
    typeof value.error === 'string' &&
    typeof value.code === 'string' && value.code.length > 0 &&
    typeof value.message === 'string' &&
    typeof value.path === 'string' &&
    typeof value.requestId === 'string' &&
    (value.fieldErrors === null || value.fieldErrors === undefined || isFieldErrors(value.fieldErrors))
  )
}

export function getApiError(error: unknown): ApiErrorResponse | null {
  if (axios.isAxiosError(error) && isApiErrorResponse(error.response?.data)) {
    return error.response.data
  }
  return null
}

export function getSafeErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const apiError = getApiError(error)
  if (apiError) return apiError.message
  if (axios.isAxiosError(error) && !error.response) {
    return 'Unable to reach UniDoc. Check your connection and try again.'
  }
  return fallback
}

export function getRequestId(error: unknown): string | null {
  return getApiError(error)?.requestId ?? null
}
