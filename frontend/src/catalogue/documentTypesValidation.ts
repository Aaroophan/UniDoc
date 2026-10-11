import type { FieldErrors } from '../api/contracts'
import type { AdminDocumentType, SaveDocumentType } from './documentTypesApi'

export interface DocumentTypeForm {
  code: string
  name: string
  description: string
  requirements: string
  supportingDocumentsRequired: boolean
  electronicAvailable: boolean
  physicalAvailable: boolean
  collectionLocation: string
  collectionInstructions: string
  active: boolean
}

export const BLANK_DOCUMENT_TYPE: DocumentTypeForm = {
  code: '', name: '', description: '', requirements: '',
  supportingDocumentsRequired: false, electronicAvailable: true, physicalAvailable: true,
  collectionLocation: '', collectionInstructions: '', active: true,
}

export function asForm(value: AdminDocumentType): DocumentTypeForm {
  return {
    code: value.code, name: value.name, description: value.description, requirements: value.requirements,
    supportingDocumentsRequired: value.supportingDocumentsRequired,
    electronicAvailable: value.electronicAvailable, physicalAvailable: value.physicalAvailable,
    collectionLocation: value.collectionLocation ?? '', collectionInstructions: value.collectionInstructions ?? '',
    active: value.active,
  }
}

export function normalizedText(value: string): string {
  return value.trim().replace(/\s+/gu, ' ')
}

export function toPayload(form: DocumentTypeForm): SaveDocumentType {
  return {
    code: form.code.trim().toUpperCase(), name: normalizedText(form.name),
    description: normalizedText(form.description), requirements: normalizedText(form.requirements),
    supportingDocumentsRequired: form.supportingDocumentsRequired,
    electronicAvailable: form.electronicAvailable, physicalAvailable: form.physicalAvailable,
    collectionLocation: normalizedText(form.collectionLocation) || null,
    collectionInstructions: normalizedText(form.collectionInstructions) || null,
    active: form.active,
  }
}

export function validateDocumentType(form: DocumentTypeForm): FieldErrors {
  const values = toPayload(form)
  const errors: FieldErrors = {}
  if (values.code.length < 2 || values.code.length > 50 || !/^[A-Z0-9_-]+$/.test(values.code))
    errors.code = 'Use 2–50 letters, numbers, underscores or hyphens.'
  if (values.name.length < 2 || values.name.length > 150) errors.name = 'Enter a name between 2 and 150 characters.'
  if (values.description.length < 10 || values.description.length > 2000) errors.description = 'Enter a description between 10 and 2000 characters.'
  if (values.requirements.length < 3 || values.requirements.length > 4000) errors.requirements = 'Enter requirements between 3 and 4000 characters.'
  if (!values.electronicAvailable && !values.physicalAvailable) errors.electronicAvailable = 'Enable at least one delivery method.'
  if (values.physicalAvailable && !values.collectionLocation) errors.collectionLocation = 'Collection location is required for physical delivery.'
  else if (values.collectionLocation && values.collectionLocation.length > 255) errors.collectionLocation = 'Collection location must not exceed 255 characters.'
  if (values.collectionInstructions && values.collectionInstructions.length > 2000) errors.collectionInstructions = 'Collection instructions must not exceed 2000 characters.'
  return errors
}
