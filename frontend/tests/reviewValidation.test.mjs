import test from 'node:test'
import assert from 'node:assert/strict'
import { validateReviewText } from '../src/requests/reviewValidation.ts'

test('staff information request and rejection require 5 to 2000 trimmed characters', () => {
  for (const kind of ['information-request', 'rejection']) {
    for (const input of ['', '   ', ' x ', 'four', 'x'.repeat(2001)]) {
      assert.ok(validateReviewText(input, kind), `${kind}: ${JSON.stringify(input)}`)
    }
    assert.equal(validateReviewText(' 12345 ', kind), null)
    assert.equal(validateReviewText('a'.repeat(2000), kind), null)
  }
})

test('student response requires 2 to 2000 trimmed characters', () => {
  for (const input of ['', ' ', ' x ', 'a'.repeat(2001)]) {
    assert.ok(validateReviewText(input, 'information-response'))
  }
  assert.equal(validateReviewText('  ok  ', 'information-response'), null)
  assert.equal(validateReviewText('a'.repeat(2000), 'information-response'), null)
})
