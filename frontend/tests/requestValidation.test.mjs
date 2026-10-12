import test from 'node:test'
import assert from 'node:assert/strict'
import { localToday, isValidLocalDate, validateStudentRequest, validateSupportingFiles, MAX_SUPPORTING_FILE_BYTES } from '../src/requests/requestValidation.ts'

const type = { id: 7, availableDeliveryMethods: ['ELECTRONIC'], supportingDocumentsRequired: false }
const valid = { documentTypeId: 7, purpose: 'Academic scholarship application', requiredDate: '2026-10-19', deliveryMethod: 'ELECTRONIC' }
const file = (name, size = 1234) => ({ name, size })

test('formats local dates rather than UTC', () => {
  assert.equal(localToday(new Date(2026, 9, 12, 8, 15)), '2026-10-12')
  assert.equal(localToday(new Date(2026, 0, 1, 23, 30)), '2026-01-01')
})
test('rejects invalid and impossible calendar dates', () => {
  for (const value of ['', '2026-02-29', '2026-13-01', '2026-04-31', '2026-00-10', '2026-1-1', 'abc']) {
    assert.equal(isValidLocalDate(value), false, value)
  }
  assert.equal(isValidLocalDate('2028-02-29'), true)
})
test('accepts a valid optional-file request', () => {
  assert.deepEqual(validateStudentRequest(valid, type, [], '2026-10-12'), {})
})
test('accepts a date equal to local today', () => {
  assert.deepEqual(validateStudentRequest({ ...valid, requiredDate: '2026-10-12' }, type, [], '2026-10-12'), {})
})
test('rejects past dates', () => {
  assert.match(validateStudentRequest({ ...valid, requiredDate: '2026-10-11' }, type, [], '2026-10-12').requiredDate, /past/)
})
test('validates purpose length after trimming', () => {
  assert.ok(validateStudentRequest({ ...valid, purpose: '  no ' }, type, [], '2026-10-12').purpose)
  assert.ok(validateStudentRequest({ ...valid, purpose: 'a'.repeat(1001) }, type, [], '2026-10-12').purpose)
  assert.deepEqual(validateStudentRequest({ ...valid, purpose: '12345' }, type, [], '2026-10-12'), {})
})
test('validates current catalogue type and enabled delivery', () => {
  assert.ok(validateStudentRequest({ ...valid, documentTypeId: 8 }, type, [], '2026-10-12').documentTypeId)
  assert.ok(validateStudentRequest({ ...valid, deliveryMethod: 'PHYSICAL' }, type, [], '2026-10-12').deliveryMethod)
})
test('requires supporting files only for required types', () => {
  assert.ok(validateStudentRequest(valid, { ...type, supportingDocumentsRequired: true }, [], '2026-10-12').files)
  assert.equal(validateSupportingFiles([], false), null)
  assert.equal(validateSupportingFiles([file('photo.jpg')], true), null)
})
test('accepts PDF/JPEG/PNG extensions without case sensitivity', () => {
  assert.equal(validateSupportingFiles(['proof.PDF', 'photo.Jpeg', 'photo.PNG'].map((name) => file(name)), false), null)
})
test('rejects forbidden extensions (the server still validates signatures)', () => {
  assert.match(validateSupportingFiles([file('notes.txt')], false), /PDF/)
  assert.match(validateSupportingFiles([file('report.pdf.exe')], false), /PDF/)
})
test('enforces three supporting files at most', () => {
  assert.match(validateSupportingFiles(Array.from({ length: 4 }, () => file('x.pdf')), false), /three/)
  assert.equal(validateSupportingFiles(Array.from({ length: 3 }, () => file('x.pdf')), false), null)
})
test('enforces the exact 5 MiB boundary', () => {
  assert.equal(validateSupportingFiles([file('x.pdf', MAX_SUPPORTING_FILE_BYTES)], false), null)
  assert.ok(validateSupportingFiles([file('x.pdf', MAX_SUPPORTING_FILE_BYTES + 1)], false))
})
test('rejects empty file bytes', () => {
  assert.ok(validateSupportingFiles([file('empty.pdf', 0)], false))
})
test('includes file errors in request validation', () => {
  assert.ok(validateStudentRequest(valid, type, [file('bad.txt')], '2026-10-12').files)
})
