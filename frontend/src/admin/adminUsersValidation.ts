import type { FieldErrors } from '../api/contracts'
import { normalizeEmail } from '../auth/validation'

const upper = /\p{Lu}/u
const lower = /\p{Ll}/u
const digit = /\p{Nd}/u
const special = /[^\p{L}\p{N}\s]/u

export interface CreateAccountForm {
  fullName: string
  email: string
  role: 'STAFF' | 'ADMIN'
  password: string
  confirmPassword: string
}

export interface PasswordResetForm {
  newPassword: string
  confirmPassword: string
}

function passwordIssue(value: string): string | null {
  if (value.length < 8 || value.length > 72) return 'Use between 8 and 72 characters.'
  if (![upper, lower, digit, special].every((pattern) => pattern.test(value))) {
    return 'Include uppercase, lowercase, a number and a special character.'
  }
  return null
}

export function validateManagedAccount(values: CreateAccountForm): FieldErrors {
  const errors: FieldErrors = {}
  const email = normalizeEmail(values.email)
  if (values.fullName.trim().length < 2 || values.fullName.trim().length > 150) {
    errors.fullName = 'Full name must be between 2 and 150 characters.'
  }
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(email)) errors.email = 'Enter a valid email address.'
  if (values.role !== 'STAFF' && values.role !== 'ADMIN') errors.role = 'Choose staff or administrator.'
  const password = passwordIssue(values.password)
  if (password) errors.password = password
  if (!values.confirmPassword) errors.confirmPassword = 'Confirm the password.'
  else if (values.confirmPassword !== values.password) errors.confirmPassword = 'Passwords do not match.'
  return errors
}

export function validatePasswordReset(values: PasswordResetForm): FieldErrors {
  const errors: FieldErrors = {}
  const password = passwordIssue(values.newPassword)
  if (password) errors.newPassword = password
  if (!values.confirmPassword) errors.confirmPassword = 'Confirm the new password.'
  else if (values.confirmPassword !== values.newPassword) errors.confirmPassword = 'Passwords do not match.'
  return errors
}
