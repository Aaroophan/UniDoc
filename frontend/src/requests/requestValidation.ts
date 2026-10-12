import type { DeliveryMethod } from '../api/contracts'
import type { CatalogueDocumentType } from '../catalogue/documentTypesApi'
import type { CreateStudentRequest } from './requestsApi'

export const MAX_SUPPORTING_FILES = 3
export const MAX_SUPPORTING_FILE_BYTES = 5 * 1024 * 1024
const FILE_EXTENSION = /\.(pdf|jpe?g|png)$/i
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

export type RequestFormErrors = Partial<Record<'purpose' | 'requiredDate' | 'deliveryMethod' | 'files' | 'documentTypeId', string>>

/** Use the browser's calendar date (not UTC's date), matching the local backend host. */
export function localToday(today: Date = new Date()): string {
  return [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-')
}

export function isValidLocalDate(value: string): boolean {
  const match = ISO_DATE.exec(value)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > 31) return false
  const test = new Date(0)
  test.setFullYear(year, month - 1, day)
  return test.getFullYear() === year && test.getMonth() === month - 1 && test.getDate() === day
}

export function validateSupportingFiles(files: readonly Pick<File, 'name' | 'size'>[], required: boolean): string | null {
  if (required && files.length === 0) return 'Add at least one supporting document for this document type.'
  if (files.length > MAX_SUPPORTING_FILES) return 'Choose no more than three supporting documents.'
  for (const file of files) {
    if (!FILE_EXTENSION.test(file.name)) return 'Only PDF, JPG, JPEG, and PNG files are supported.'
    if (file.size <= 0) return 'Selected files must not be empty.'
    if (file.size > MAX_SUPPORTING_FILE_BYTES) return 'Each supporting document must be 5 MiB or smaller.'
  }
  return null
}

export function validateStudentRequest(
  values: CreateStudentRequest,
  type: Pick<CatalogueDocumentType, 'id' | 'availableDeliveryMethods' | 'supportingDocumentsRequired'>,
  files: readonly Pick<File, 'name' | 'size'>[],
  today: string = localToday(),
): RequestFormErrors {
  const errors: RequestFormErrors = {}
  const purposeLength = values.purpose.trim().length
  if (purposeLength < 5 || purposeLength > 1000) errors.purpose = 'Enter a purpose between 5 and 1000 characters.'
  if (!isValidLocalDate(values.requiredDate)) errors.requiredDate = 'Choose a valid required date.'
  else if (values.requiredDate < today) errors.requiredDate = 'The required date cannot be in the past.'
  if (!type.availableDeliveryMethods.includes(values.deliveryMethod as DeliveryMethod)) {
    errors.deliveryMethod = 'Choose an available delivery method.'
  }
  if (values.documentTypeId !== type.id || !Number.isSafeInteger(values.documentTypeId) || values.documentTypeId < 1) {
    errors.documentTypeId = 'Choose an available document type.'
  }
  const filesError = validateSupportingFiles(files, type.supportingDocumentsRequired)
  if (filesError) errors.files = filesError
  return errors
}
