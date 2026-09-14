/**
 * Claim extraction.
 *
 * The patterns below are the whole definition of what this check can promise. They are deliberately
 * a short list of regular forms rather than a markdown parser: the claim surface is about a dozen
 * shapes, and a syntax tree would be effort spent on prose, which FR-006 says never to interpret.
 *
 * Contract: specs/009-spec-drift-check/contracts/claim-grammar.md.
 */

/**
 * What this check does not attempt, declared once. `report.mjs` prints this list and must not keep
 * its own copy — a second hand-written list of the boundary would be exactly the drift this whole
 * feature exists to catch (FR-013).
 */
export const NOT_CHECKED = [
  'prose',
  'accuracy of descriptions',
  'external links',
  'code behaviour',
]

export const CLAIM_KINDS = ['path', 'command', 'requirement', 'reference', 'status']

/**
 * The status claim: one per feature, about its own specification (research R-001).
 *
 * The only kind that checks a document against another document rather than against code. It is here
 * because `Status` arms every other check, and the worst thing this tool could do is pass by not
 * looking.
 */
export function statusClaims(feature) {
  const spec = feature.documents.find((d) => d.endsWith('/spec.md'))
  if (!spec) return []
  return [
    {
      kind: 'status',
      document: spec,
      line: 1,
      raw: feature.declaredStatus,
      subject: feature.declaredStatus,
      feature: feature.id,
    },
  ]
}
