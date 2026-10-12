/** Filenames from HTTP headers and uploaded metadata are untrusted display strings. */
export function safeDownloadFilename(raw: string | null | undefined): string {
  if (!raw) return 'download'
  let name = Array.from(raw.normalize('NFC'))
    .map((character) => {
      const code = character.codePointAt(0) ?? 0
      if (code < 32 || (code >= 127 && code <= 159)) return '_'
      if (code === 0x202a || code === 0x202b || code === 0x202c || code === 0x202d || code === 0x202e ||
          (code >= 0x2066 && code <= 0x2069)) return ''
      if ('\\/"<>:|?*'.includes(character)) return '_'
      return character
    })
    .join('')
    .trim()
    .replace(/^[. ]+|[. ]+$/g, '')
  name = Array.from(name).slice(0, 160).join('').replace(/[. ]+$/g, '')
  if (!name) return 'download'
  // Windows reserves these device names even when followed by an extension.
  const stem = name.split('.')[0]
  if (/^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/i.test(stem)) name = `_${name}`
  return name
}

/** RFC 5987 filename* takes precedence; malformed encodings fall back to filename. */
export function filenameFromDisposition(header: string | undefined, fallback: string): string {
  if (!header) return safeDownloadFilename(fallback)
  const extended = /(?:^|;)\s*filename\*\s*=\s*(?:"?UTF-8'[^']*'([^;"\s]+)"?)/i.exec(header)
  if (extended) {
    try { return safeDownloadFilename(decodeURIComponent(extended[1])) }
    catch { /* Try plain filename or the metadata fallback instead. */ }
  }
  const normal = /(?:^|;)\s*filename\s*=\s*(?:"((?:\\.|[^"\\])*)"|([^;]*))/i.exec(header)
  if (normal) {
    const value = normal[1] === undefined ? normal[2].trim() : normal[1].replace(/\\(.)/g, '$1')
    if (value) return safeDownloadFilename(value)
  }
  return safeDownloadFilename(fallback)
}
