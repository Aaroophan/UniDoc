import test from 'node:test'
import assert from 'node:assert/strict'
import { ISSUED_PDF_MAX_BYTES, validateIssuedPdfCandidate } from '../src/staff/issuedPdfValidation.ts'

const pdf = (name = 'document.pdf', size = 1200, type = 'application/pdf') => ({ name, size, type })

test('one non-empty PDF at the exact 10 MiB boundary is allowed', () => {
  assert.equal(validateIssuedPdfCandidate(pdf()), null)
  assert.equal(validateIssuedPdfCandidate(pdf('Diploma.PDF', ISSUED_PDF_MAX_BYTES)), null)
  assert.equal(validateIssuedPdfCandidate(pdf('document.pdf', 100, '')), null)
})

test('missing, zero-byte and oversized PDFs are rejected', () => {
  assert.match(validateIssuedPdfCandidate(null), /Choose/)
  assert.match(validateIssuedPdfCandidate(pdf('x.pdf', 0)), /empty/)
  assert.match(validateIssuedPdfCandidate(pdf('x.pdf', ISSUED_PDF_MAX_BYTES + 1)), /10 MiB/)
})

test('PDF extension and browser MIME are checked independently', () => {
  assert.match(validateIssuedPdfCandidate(pdf('wrong.png')), /PDF/)
  assert.match(validateIssuedPdfCandidate(pdf('document.pdf.exe')), /PDF/)
  assert.match(validateIssuedPdfCandidate(pdf('document.pdf', 123, 'text/plain')), /PDF/)
})
