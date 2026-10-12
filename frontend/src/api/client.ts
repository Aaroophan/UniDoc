import { filenameFromDisposition } from './downloadNames'
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
