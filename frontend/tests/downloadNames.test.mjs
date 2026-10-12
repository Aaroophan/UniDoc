import test from 'node:test'
import assert from 'node:assert/strict'
import { safeDownloadFilename, filenameFromDisposition } from '../src/api/downloadNames.ts'

test('uses metadata fallback for missing header', () => {
  assert.equal(filenameFromDisposition(undefined, 'result.pdf'), 'result.pdf')
})
test('decodes UTF-8 extended attachment name', () => {
  assert.equal(filenameFromDisposition("attachment; filename=ascii.pdf; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf", 'fallback.pdf'), 'resume.pdf'.replace('resume', 'r\u00e9sum\u00e9'))
})
test('uses quoted attachment name', () => {
  assert.equal(filenameFromDisposition('attachment; filename="test document.pdf"', 'fallback.pdf'), 'test document.pdf')
})
test('handles escaped quotes in a quoted name', () => {
  assert.equal(filenameFromDisposition('attachment; filename="report\\"draft.pdf"', 'fallback.pdf'), 'report_draft.pdf')
})
test('falls back to plain name when extended encoding is invalid', () => {
  assert.equal(filenameFromDisposition("attachment; filename=plain.pdf; filename*=UTF-8''%ZZ", 'fallback.pdf'), 'plain.pdf')
})
test('does not permit paths or Windows-reserved device names', () => {
  assert.equal(safeDownloadFilename('../CON.pdf'), '_CON.pdf')
  assert.equal(safeDownloadFilename('AUX.txt'), '_AUX.txt')
  assert.equal(safeDownloadFilename('subdir/file.pdf'), 'subdir_file.pdf')
})
test('strips direction controls and newline injection', () => {
  assert.equal(safeDownloadFilename('a\u202eb\nc.pdf'), 'ab_c.pdf')
})
test('bounds names and rejects path-only strings', () => {
  assert.equal(safeDownloadFilename('a'.repeat(200)).length, 160)
  assert.equal(safeDownloadFilename('... '), 'download')
})
