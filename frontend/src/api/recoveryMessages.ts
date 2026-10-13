/** Stable end-user recovery copy. Never echo server exception text or file paths. */
export function recoveryMessageForApi(status: number, code: string): string | null {
  switch (code) {
    case 'AUTHENTICATION_REQUIRED':
    case 'INVALID_TOKEN':
    case 'TOKEN_EXPIRED':
    case 'SESSION_REVOKED':
      return 'Your session ended. Sign in again to continue.'
    case 'ACCOUNT_INACTIVE':
      return 'This account is inactive. Contact an administrator.'
    case 'ACCESS_DENIED':
      return 'Your account does not have permission to perform this action.'
    case 'REQUEST_VERSION_CONFLICT':
      return 'This request changed. Reload the latest status and history before choosing an action again.'
    case 'FILE_STORAGE_ERROR':
      return 'This file is temporarily unavailable. Reload and try again, or contact an administrator.'
    case 'DOCUMENT_NOT_READY':
      return 'The document is not ready yet. Check the latest request status.'
    case 'INTERNAL_ERROR':
    case 'SERVICE_UNAVAILABLE':
      return 'UniDoc could not complete that request. Try again later and include the request ID if you need support.'
    default:
      return status >= 500 ? 'UniDoc could not complete that request. Please try again later.' : null
  }
}

export function networkRecoveryMessage(method: string | undefined): string {
  if (method && !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    return 'The connection was interrupted. The action may have completed. Check the latest information before trying again.'
  }
  return 'Unable to reach UniDoc. Check your connection and try again.'
}
