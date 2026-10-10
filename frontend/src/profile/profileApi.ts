import { apiClient } from '../api/client'

/** Exact student-owned profile contract from 05-api-design.md. */
export interface StudentProfileResponse {
  userId: number
  email: string
  fullName: string
  registrationNumber: string | null
  phone: string | null
  faculty: string | null
  department: string | null
  degreeProgramme: string | null
  academicYear: number | null
  complete: boolean
  updatedAt: string | null
}

export interface UpdateStudentProfileRequest {
  fullName: string
  registrationNumber: string
  phone: string | null
  faculty: string
  department: string
  degreeProgramme: string
  academicYear: number
}

const PROFILE_PATH = '/api/student/profile'

export async function getStudentProfile(signal?: AbortSignal): Promise<StudentProfileResponse> {
  const response = await apiClient.get<StudentProfileResponse>(PROFILE_PATH, { signal })
  return response.data
}

export async function updateStudentProfile(values: UpdateStudentProfileRequest): Promise<StudentProfileResponse> {
  const response = await apiClient.put<StudentProfileResponse>(PROFILE_PATH, values)
  return response.data
}

/** False on incomplete or structurally invalid responses; never inferred from a failed request. */
export function hasCompleteStudentProfile(profile: StudentProfileResponse): boolean {
  return profile.complete === true &&
    typeof profile.fullName === 'string' && profile.fullName.trim().length >= 2 &&
    typeof profile.registrationNumber === 'string' && profile.registrationNumber.trim().length >= 2 &&
    typeof profile.faculty === 'string' && profile.faculty.trim().length >= 2 &&
    typeof profile.department === 'string' && profile.department.trim().length >= 2 &&
    typeof profile.degreeProgramme === 'string' && profile.degreeProgramme.trim().length >= 2 &&
    typeof profile.academicYear === 'number' && Number.isInteger(profile.academicYear) &&
    profile.academicYear >= 1 && profile.academicYear <= 8
}
