import { filenameFromDisposition } from './downloadNames'
import axios, { AxiosHeaders } from 'axios'
import { getApiError, isApiErrorResponse } from './errors'
import { endSession, getStoredSession } from '../auth/session'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || 'http://localhost:8080'

/** One browser/API boundary; API paths belong to feature modules. */
export const apiClient = axios.create({
  baseURL: configuredBaseUrl.replace(/\/+$/, ''),
  headers: { Accept: 'application/json' },
  withCredentials: false,
  timeout: 15000,
})

apiClient.interceptors.request.use((config) => {
  const session = getStoredSession()
  if (session) {
    config.headers = AxiosHeaders.from(config.headers)
    config.headers.set('Authorization', `Bearer ${session.token}`)
  }
  return config
})

/** Axios gives binary endpoints Blob errors; decode only bounded JSON envelopes first. */
async function recoverBinaryApiError(error: unknown): Promise<void> {
  if (!axios.isAxiosError(error)) return
  const data: unknown = error.response?.data
  if (!(data instanceof Blob) || data.size > 64 * 1024) return
  const contentType = data.type || String(error.response?.headers?.['content-type'] ?? '')
  if (!/(?:application\/json|application\/problem\+json)/i.test(contentType)) return
  try {
    const decoded: unknown = JSON.parse(await data.text())
    if (isApiErrorResponse(decoded) && error.response) error.response.data = decoded
  } catch {
    // Unrecognized or malformed server content must never appear in the UI.
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (axios.isAxiosError(error)) {
      await recoverBinaryApiError(error)
      const url = error.config?.url?.split('?')[0] ?? ''
      // Public credential failures must not terminate another valid session.
      const isPublicAuth = url.endsWith('/api/auth/login') || url.endsWith('/api/auth/register')
      if (!isPublicAuth) {
        const code = getApiError(error)?.code
        const authHeader = AxiosHeaders.from(error.config?.headers).get('Authorization')
        const issuedToken = typeof authHeader === 'string' && authHeader.startsWith('Bearer ')
          ? authHeader.slice(7) : null
        if (issuedToken) {
          if (error.response?.status === 401) {
            endSession('expired', issuedToken)
          } else if (error.response?.status === 403 && code === 'ACCOUNT_INACTIVE') {
            endSession('inactive', issuedToken)
          }
          // 403 ACCESS_DENIED preserves an otherwise valid session.
        }
      }
    }
    return Promise.reject(error)
  },
)

export interface BinaryResponse {
  blob: Blob
  filename: string
  contentType: string
}

/** Protected browser downloads. Do not construct public filesystem URLs. */
export async function getBinaryResource(path: string, fallbackName = 'download'): Promise<BinaryResponse> {
  const response = await apiClient.get<Blob>(path, { responseType: 'blob' })
  const disposition = response.headers['content-disposition']
  const filename = filenameFromDisposition(typeof disposition === 'string' ? disposition : undefined, fallbackName)
  return {
    blob: response.data,
    filename,
    contentType: typeof response.headers['content-type'] === 'string' ? response.headers['content-type'] : 'application/octet-stream',
  }
}
