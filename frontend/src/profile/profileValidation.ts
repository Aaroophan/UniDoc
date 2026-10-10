import type { FieldErrors } from '../api/contracts'
import type { StudentProfileResponse, UpdateStudentProfileRequest } from './profileApi'

/** String form state keeps an empty academic year distinguishable from year zero. */
export interface ProfileFormValues {
  fullName: string
  registrationNumber: string
  phone: string
  faculty: string
  department: string
  degreeProgramme: string
  academicYear: string
}

export type ProfileField = keyof ProfileFormValues

export const PROFILE_FIELDS: readonly ProfileField[] = [
  'fullName', 'registrationNumber', 'phone', 'faculty', 'department', 'degreeProgramme', 'academicYear',
]

export function formValuesFromProfile(profile: StudentProfileResponse): ProfileFormValues {
  return {
    fullName: profile.fullName,
    registrationNumber: profile.registrationNumber ?? '',
    phone: profile.phone ?? '',
    faculty: profile.faculty ?? '',
    department: profile.department ?? '',
    degreeProgramme: profile.degreeProgramme ?? '',
    academicYear: profile.academicYear == null ? '' : String(profile.academicYear),
  }
}

function collapsed(value: string): string {
  return value.trim().replace(/\s+/gu, ' ')
}

export function toUpdateRequest(values: ProfileFormValues): UpdateStudentProfileRequest {
  return {
    fullName: collapsed(values.fullName),
    registrationNumber: values.registrationNumber.trim(),
    phone: values.phone.trim() ? collapsed(values.phone) : null,
    faculty: collapsed(values.faculty),
    department: collapsed(values.department),
    degreeProgramme: collapsed(values.degreeProgramme),
    academicYear: Number(values.academicYear),
  }
}

/** Fast feedback only; backend validation remains authoritative. */
export function validateProfile(values: ProfileFormValues): FieldErrors {
  const errors: FieldErrors = {}
  for (const [field, label, max] of [
    ['fullName', 'Full name', 150],
    ['faculty', 'Faculty', 100],
    ['department', 'Department', 100],
    ['degreeProgramme', 'Degree programme', 150],
  ] as const) {
    const length = values[field].trim().length
    if (length < 2 || length > max) errors[field] = `${label} must be between 2 and ${max} characters.`
  }
  const registration = values.registrationNumber.trim()
  if (registration.length < 2 || registration.length > 50) {
    errors.registrationNumber = 'Registration number must be between 2 and 50 characters.'
  } else if (!/^[A-Za-z0-9/-]+$/.test(registration)) {
    errors.registrationNumber = 'Use only letters, numbers, slash and hyphen.'
  }
  const phone = values.phone.trim()
  if (phone && (phone.length < 7 || phone.length > 30 || !/^[+0-9 ()-]+$/.test(phone) || !/[0-9]/.test(phone))) {
    errors.phone = 'Phone must be 7 to 30 characters using numbers, spaces, +, - or parentheses.'
  }
  if (!/^[1-8]$/.test(values.academicYear.trim())) {
    errors.academicYear = 'Choose an academic year from 1 to 8.'
  }
  return errors
}
