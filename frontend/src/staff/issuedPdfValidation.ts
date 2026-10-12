/** Browser guidance only. The server validates MIME, signature and 10 MiB limit. */
export const ISSUED_PDF_MAX_BYTES = 10 * 1024 * 1024

export interface IssuedPdfCandidate {
  name: string
  size: number
  type: string
}

export function validateIssuedPdfCandidate(file: IssuedPdfCandidate | null | undefined): string | null {
  if (!file) return 'Choose the issued PDF before continuing.'
  if (file.size < 1) return 'The PDF must not be empty.'
  if (file.size > ISSUED_PDF_MAX_BYTES) return 'The issued PDF must be 10 MiB or smaller.'
  if (!/\.pdf$/i.test(file.name)) return 'Choose a PDF file (.pdf).'
  if (file.type && file.type.toLowerCase() !== 'application/pdf') return 'The selected file must be a PDF.'
  return null
}
