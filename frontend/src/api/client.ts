import axios, { AxiosHeaders } from 'axios'
import { getApiError } from './errors'
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

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      const url = error.config?.url?.split('?')[0] ?? ''
      // Wrong credentials at the public login endpoint must never end a session.
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
          // 403 ACCESS_DENIED is a normal authorization failure, not logout.
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

function safeFilename(value: string): string {
  const safe = value.replace(/[\\/\x00-\x1f\x7f"<>:|?*]/g, '_').trim().slice(0, 160)
  return safe || 'download'
}

/** For later protected downloads. Do not construct public filesystem URLs. */
export async function getBinaryResource(path: string, fallbackName = 'download'): Promise<BinaryResponse> {
  const response = await apiClient.get<Blob>(path, { responseType: 'blob' })
  const disposition = response.headers['content-disposition']
  let filename = safeFilename(fallbackName)
  if (typeof disposition === 'string') {
    const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    const quoted = disposition.match(/filename="([^"]*)"/i)
    const plain = disposition.match(/filename=([^;]+)/i)
    try {
      if (encoded) filename = safeFilename(decodeURIComponent(encoded[1]))
      else if (quoted) filename = safeFilename(quoted[1])
      else if (plain) filename = safeFilename(plain[1].trim())
    } catch { /* Use safe fallback if the server filename cannot be decoded. */ }
  }
  return {
    blob: response.data,
    filename,
    contentType: typeof response.headers['content-type'] === 'string' ? response.headers['content-type'] : 'application/octet-stream',
  }
}
