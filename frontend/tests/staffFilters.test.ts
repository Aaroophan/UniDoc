import assert from 'node:assert/strict'
import test from 'node:test'
import { staffFilterParameters } from '../src/staff/filterParameters.ts'

test('staff work-list filters include search, state, method, dates, type and pagination', () => {
  const query = staffFilterParameters({
    q: '  UDR-000048  ', status: 'SUBMITTED', deliveryMethod: 'PHYSICAL',
    documentTypeId: 22, submittedFrom: '2026-10-01', submittedTo: '2026-10-12',
    requiredDate: '2026-10-21', sort: 'requiredDate', direction: 'asc', page: 3, size: 10,
  })
  assert.equal(query.get('q'), 'UDR-000048')
  assert.equal(query.get('status'), 'SUBMITTED')
  assert.equal(query.get('deliveryMethod'), 'PHYSICAL')
  assert.equal(query.get('documentTypeId'), '22')
  assert.equal(query.get('submittedFrom'), '2026-10-01')
  assert.equal(query.get('submittedTo'), '2026-10-12')
  assert.equal(query.get('requiredDate'), '2026-10-21')
  assert.equal(query.get('sort'), 'requiredDate')
  assert.equal(query.get('direction'), 'asc')
  assert.equal(query.get('page'), '3')
  assert.equal(query.get('size'), '10')
})

test('blank optional filters are omitted rather than sent as empty strings', () => {
  const query = staffFilterParameters({ q: '    ', status: '', deliveryMethod: '', page: 0 })
  assert.equal(query.has('q'), false)
  assert.equal(query.has('status'), false)
  assert.equal(query.has('deliveryMethod'), false)
  assert.equal(query.get('page'), '0')
})

test('type IDs must be positive safe integers', () => {
  for (const id of [0, -2, 1.5, Number.MAX_SAFE_INTEGER + 1, Number.NaN]) {
    assert.throws(() => staffFilterParameters({ documentTypeId: id }), /Invalid document type identifier/)
  }
})

test('query values are encoded by URLSearchParams, not interpolated into paths', () => {
  const query = staffFilterParameters({ q: 'a&status=APPROVED ?#' })
  assert.equal(query.get('q'), 'a&status=APPROVED ?#')
  assert.equal(query.has('status'), false)
})
