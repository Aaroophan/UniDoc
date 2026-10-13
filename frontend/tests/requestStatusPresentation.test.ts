import { test } from 'node:test'
import { strict as assert } from 'node:assert'
import { presentRequestStatus, STATUS_PRESENTATIONS } from '../src/components/requestStatusPresentation.ts'
import { REQUEST_STATUSES } from '../src/api/contracts.ts'

test('all eight request statuses have distinct readable text and an explanation', () => {
  assert.equal(REQUEST_STATUSES.length, 8)
  assert.deepEqual(Object.keys(STATUS_PRESENTATIONS).sort(), [...REQUEST_STATUSES].sort())
  for (const status of REQUEST_STATUSES) {
    const value = presentRequestStatus(status)
    assert.ok(value.label.length > 2)
    assert.ok(value.explanation.length > 15)
    assert.ok(value.symbol.length > 0)
  }
})

test('attention and terminal statuses communicate meaning without relying on color', () => {
  assert.equal(presentRequestStatus('ADDITIONAL_INFORMATION_REQUIRED').tone, 'warning')
  assert.equal(presentRequestStatus('REJECTED').label, 'Rejected')
  assert.equal(presentRequestStatus('COMPLETED').label, 'Completed')
})

test('unknown server values have a visible safe fallback', () => {
  assert.equal(presentRequestStatus('FUTURE_STATE').label, 'Unknown status')
  assert.match(presentRequestStatus('FUTURE_STATE').explanation, /unrecognized/i)
})
