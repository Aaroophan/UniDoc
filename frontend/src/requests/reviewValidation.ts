/** Client-side guidance only. The API remains authoritative for each written decision. */
export type ReviewTextKind = 'information-request' | 'rejection' | 'information-response'

export function validateReviewText(value: string, kind: ReviewTextKind): string | null {
  const minimum = kind === 'information-response' ? 2 : 5
  const subject = kind === 'rejection' ? 'reason' : kind === 'information-request' ? 'message' : 'response'
  const length = value.trim().length
  return length < minimum || length > 2000
    ? `Enter a ${subject} between ${minimum} and 2000 characters.` : null
}
