import type { FieldErrors, LoginRequest, RegisterRequest } from '../api/contracts'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/
const UPPER = /\p{Lu}/u
const LOWER = /\p{Ll}/u
const NUMBER = /\p{Nd}/u
const SPECIAL = /[^\p{L}\p{N}\s]/u

export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase()
}

export function validateLogin(values: LoginRequest): FieldErrors {
  const errors: FieldErrors = {}
  const email = normalizeEmail(values.email)
  if (!email) errors.email = 'Email is required.'
  else if (email.length > 254 || !EMAIL_PATTERN.test(email)) errors.email = 'Enter a valid email address.'
  if (!values.password) errors.password = 'Password is required.'
  else if (values.password.length > 72) errors.password = 'Password cannot exceed 72 characters.'
  return errors
}

/** Convenience checks only. The Spring Boot API enforces the actual policy. */
export function validateRegistration(values: RegisterRequest): FieldErrors {
  const errors = validateLogin(values)
  const name = values.fullName.trim()
  if (name.length < 2 || name.length > 150) errors.fullName = 'Full name must be between 2 and 150 characters.'
  if (values.password.length < 8 || values.password.length > 72) {
    errors.password = 'Use between 8 and 72 characters.'
  } else if (![UPPER, LOWER, NUMBER, SPECIAL].every((pattern) => pattern.test(values.password))) {
    errors.password = 'Include uppercase, lowercase, a number and a special character.'
  }
  if (!values.confirmPassword) errors.confirmPassword = 'Confirm your password.'
  else if (values.confirmPassword !== values.password) errors.confirmPassword = 'Passwords do not match.'
  return errors
}
