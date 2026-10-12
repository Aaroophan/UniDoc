/** Calendar dates here are local business dates, not instants. */
export function localTodayISO(date: Date = new Date()): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function validateCollectionDate(value: string, today: string = localTodayISO()): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return 'Choose a collection date.'
  const [year, month, day] = value.split('-').map(Number)
  const parsed = new Date(Date.UTC(year, month - 1, day))
  if (parsed.getUTCFullYear() !== year || parsed.getUTCMonth() + 1 !== month || parsed.getUTCDate() !== day) {
    return 'Choose a valid collection date.'
  }
  return value > today ? 'Collection date cannot be in the future.' : null
}

export function validateCollectionNote(value: string): string | null {
  return value.trim().length > 1000 ? 'Collection note cannot exceed 1000 characters.' : null
}
