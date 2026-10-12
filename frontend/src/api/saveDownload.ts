import { getBinaryResource } from './client'

/** Always fetch authenticated bytes; never expose or navigate to a private storage path. */
export async function saveAuthenticatedDownload(path: string, fallbackName: string): Promise<void> {
  const resource = await getBinaryResource(path, fallbackName)
  if (!resource.blob || resource.blob.size === 0) {
    throw new Error('The document download was empty. Please try again.')
  }

  const objectUrl = URL.createObjectURL(resource.blob)
  try {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = resource.filename
    link.style.display = 'none'
    document.body.appendChild(link)
    try { link.click() } finally { link.remove() }
  } finally {
    // Revoke after the browser has had time to begin saving the download.
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  }
}
