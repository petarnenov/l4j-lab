/**
 * An exemption states, at the claim, that this one is deliberately not checked (FR-014).
 *
 * An HTML comment, so it is invisible in rendered markdown: the bookkeeping does not leak into what
 * a reader sees. It covers the claim on its line and nothing else — disabling a whole file would
 * throw away the rest of a document that is still worth checking.
 */
const MARKER = /<!--\s*drift-ok\s*:?([^>]*)-->/

export function exemptionOn(text) {
  const found = MARKER.exec(text)
  if (!found) return null
  const reason = found[1].trim()
  // A marker with no reason is an error rather than an exemption. The reason is the whole value:
  // without it, the next reader cannot tell a deliberate choice from an abandoned one.
  if (reason.length === 0) return { error: true, reason: null }
  return { error: false, reason }
}
