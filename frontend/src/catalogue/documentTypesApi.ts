import { apiClient } from '../api/client'
import type { DeliveryMethod, PageResponse } from '../api/contracts'

const ACTIVE_PATH = '/api/document-types'
const ADMIN_PATH = '/api/admin/document-types'

export interface CatalogueDocumentType {
  id: number
  code: string
  name: string
  description: string
  requirements: string
  supportingDocumentsRequired: boolean
  availableDeliveryMethods: DeliveryMethod[]
  collectionLocation: string | null
  collectionInstructions: string | null
}

export interface AdminDocumentType {
  id: number
  code: string
  name: string
  description: string
  requirements: string
  supportingDocumentsRequired: boolean
  electronicAvailable: boolean
  physicalAvailable: boolean
  collectionLocation: string | null
  collectionInstructions: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface SaveDocumentType {
  code: string
  name: string
  description: string
  requirements: string
  supportingDocumentsRequired: boolean
  electronicAvailable: boolean
  physicalAvailable: boolean
  collectionLocation: string | null
  collectionInstructions: string | null
  active: boolean
}

export interface DocumentTypeFilters {
  q?: string
  active?: boolean | ''
  page?: number
  size?: number
  sort?: 'name' | 'code' | 'createdAt' | 'active'
  direction?: 'asc' | 'desc'
}

function safeId(id: number): number {
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('Invalid document type ID.')
  return id
}

export async function listActiveDocumentTypes(signal?: AbortSignal): Promise<CatalogueDocumentType[]> {
  return (await apiClient.get<CatalogueDocumentType[]>(ACTIVE_PATH, { signal })).data
}

export async function getActiveDocumentType(id: number, signal?: AbortSignal): Promise<CatalogueDocumentType> {
  return (await apiClient.get<CatalogueDocumentType>(`${ACTIVE_PATH}/${safeId(id)}`, { signal })).data
}

export async function listAdminDocumentTypes(filters: DocumentTypeFilters = {}, signal?: AbortSignal): Promise<PageResponse<AdminDocumentType>> {
  const params = new URLSearchParams()
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  if (typeof filters.active === 'boolean') params.set('active', String(filters.active))
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  if (filters.sort) params.set('sort', filters.sort)
  if (filters.direction) params.set('direction', filters.direction)
  return (await apiClient.get<PageResponse<AdminDocumentType>>(ADMIN_PATH, { params, signal })).data
}

export async function getAdminDocumentType(id: number, signal?: AbortSignal): Promise<AdminDocumentType> {
  return (await apiClient.get<AdminDocumentType>(`${ADMIN_PATH}/${safeId(id)}`, { signal })).data
}

export async function createDocumentType(values: SaveDocumentType): Promise<AdminDocumentType> {
  return (await apiClient.post<AdminDocumentType>(ADMIN_PATH, values)).data
}

export async function updateDocumentType(id: number, values: SaveDocumentType): Promise<AdminDocumentType> {
  return (await apiClient.put<AdminDocumentType>(`${ADMIN_PATH}/${safeId(id)}`, values)).data
}

export async function changeDocumentTypeStatus(id: number, active: boolean): Promise<AdminDocumentType> {
  return (await apiClient.patch<AdminDocumentType>(`${ADMIN_PATH}/${safeId(id)}/status`, { active })).data
}
