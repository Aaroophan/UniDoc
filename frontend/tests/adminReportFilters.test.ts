import assert from 'node:assert/strict'
import test from 'node:test'
import { appliedReportUrl, readReportFilters, reportFilterParameters } from '../src/admin/reportFilters.ts'

test('report screen and CSV use exactly the same applied filter set', () => {
  const applied = {
    submittedFrom: '2026-10-01', submittedTo: '2026-10-12',
    status: 'COMPLETED' as const, documentTypeId: 12, deliveryMethod: 'PHYSICAL' as const,
    sort: 'requiredDate' as const, direction: 'asc' as const, page: 3, size: 10,
  }
  const screen = reportFilterParameters(applied)
  const csv = reportFilterParameters(applied, true)
  assert.equal(screen.get('page'), '3')
  assert.equal(screen.get('size'), '10')
  assert.equal(csv.has('page'), false)
  assert.equal(csv.has('size'), false)
  for (const name of ['submittedFrom', 'submittedTo', 'status', 'documentTypeId', 'deliveryMethod', 'sort', 'direction']) {
    assert.equal(screen.get(name), csv.get(name))
  }
  assert.equal(csv.get('status'), 'COMPLETED')
  assert.equal(csv.get('documentTypeId'), '12')
})

test('blank filters are omitted and input values are query-encoded', () => {
  const query = reportFilterParameters({ status: '', deliveryMethod: '' })
  assert.equal(query.toString(), '')
  const encoded = reportFilterParameters({ submittedFrom: '2026-10-10&status=READY' })
  assert.equal(encoded.get('submittedFrom'), '2026-10-10&status=READY')
  assert.equal(encoded.has('status'), false)
})

test('invalid document type IDs do not leave browser', () => {
  for (const documentTypeId of [-1, 0, 1.5, NaN, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => reportFilterParameters({ documentTypeId }), /Invalid document type identifier/)
  }
})

test('URL persistence reads only allowed status, delivery and sort values', () => {
  const params = new URLSearchParams('status=MADE_UP&deliveryMethod=BANNED&sort=passwordHash&page=-1&documentTypeId=bad')
  const read = readReportFilters(params)
  assert.equal(read.status, undefined)
  assert.equal(read.deliveryMethod, undefined)
  assert.equal(read.sort, 'createdAt')
  assert.equal(read.documentTypeId, undefined)
  assert.equal(read.page, 0)
})

test('applied URL stores canonical filters and zero-based page without defaults', () => {
  const params = appliedReportUrl({ status: 'READY', sort: 'createdAt', direction: 'desc', page: 0 })
  assert.equal(params.toString(), 'status=READY')
  const persisted = appliedReportUrl({ ...readReportFilters(params), page: 2 })
  assert.equal(persisted.get('status'), 'READY')
  assert.equal(persisted.get('page'), '2')
})
