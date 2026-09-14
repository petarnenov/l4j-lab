/**
 * The one string that must never reach a production bundle (FR-001a).
 *
 * `scripts/check-dev-only.mjs` reads this file for the literal below rather than declaring its own
 * copy, so the check and the thing it checks for cannot drift apart. Renaming it here changes what
 * the check looks for, in the same commit.
 */
export const DEV_ONLY_MARKER = 'mcp-console-dev-only-do-not-ship'
