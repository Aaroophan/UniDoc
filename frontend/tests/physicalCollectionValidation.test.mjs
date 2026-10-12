import test from 'node:test'
import assert from 'node:assert/strict'
import { localTodayISO, validateCollectionDate, validateCollectionNote } from '../src/staff/physicalCollectionValidation.ts'

test('business dates use local calendar components rather than UTC date slicing', () => {
  const local = new Date(2026, 9, 12, 23, 42)
  assert.equal(localTodayISO(local), '2026-10-12')
})

test('collection date must be a real calendar date no later than today', () => {
  assert.equal(validateCollectionDate('2026-10-12', '2026-10-12'), null)
  assert.equal(validateCollectionDate('2026-01-31', '2026-10-12'), null)
  assert.match(validateCollectionDate('', '2026-10-12'), /Choose/)
  assert.match(validateCollectionDate('2026-02-30', '2026-10-12'), /valid/)
  assert.match(validateCollectionDate('2026-10-13', '2026-10-12'), /future/)
  assert.match(validateCollectionDate('12/10/2026', '2026-10-12'), /Choose/)
})

test('optional collection note is limited after trimming', () => {
  assert.equal(validateCollectionNote(''), null)
  assert.equal(validateCollectionNote('   '), null)
  assert.equal(validateCollectionNote('x'.repeat(1000)), null)
  assert.equal(validateCollectionNote('  ' + 'x'.repeat(1000) + '  '), null)
  assert.match(validateCollectionNote('x'.repeat(1001)), /1000/)
})
