import { test } from 'node:test'
import { strict as assert } from 'node:assert'
import { recoveryMessageForApi, networkRecoveryMessage } from '../src/api/recoveryMessages.ts'

test('session, account and ordinary permission failures have distinct recoveries', () => {
  assert.match(recoveryMessageForApi(401, 'SESSION_REVOKED') ?? '', /sign in again/i)
  assert.match(recoveryMessageForApi(403, 'ACCOUNT_INACTIVE') ?? '', /administrator/i)
  assert.match(recoveryMessageForApi(403, 'ACCESS_DENIED') ?? '', /permission/i)
})

test('version conflicts demand refetch rather than a blind repeat', () => {
  assert.match(recoveryMessageForApi(409, 'REQUEST_VERSION_CONFLICT') ?? '', /reload/i)
})

test('unexpected server errors have redacted copy and unknown mutations are treated cautiously', () => {
  assert.match(recoveryMessageForApi(500, 'NEW_ERROR') ?? '', /try again later/i)
  assert.match(networkRecoveryMessage('POST'), /may have completed/i)
  assert.match(networkRecoveryMessage('PATCH'), /check the latest/i)
  assert.match(networkRecoveryMessage('GET'), /check your connection/i)
})
