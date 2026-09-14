/**
 * One verifier per claim kind. Each returns a verdict from data-model.md and, when it fails, a
 * sentence that can be acted on without re-deriving anything (FR-011).
 */

/** The statuses the flow produces. Anything else, and nothing downstream knows what to do with it. */
export const KNOWN_STATUSES = ['Draft', 'Approved', 'Implemented']

function holds() {
  return { verdict: 'holds', why: '' }
}

function broken(why) {
  return { verdict: 'broken', why }
}

export function verifyStatus(feature) {
  const declared = feature.declaredStatus ?? ''
  const leading = declared.split(/[,.\s]/)[0]
  if (!KNOWN_STATUSES.includes(leading)) {
    return broken(
      `status "${declared}" is not one of ${KNOWN_STATUSES.join(', ')}; the check cannot tell ` +
        'whether this feature should be held to its code',
    )
  }

  if (feature.implemented && !feature.hasTasks) {
    return broken('declares itself implemented but has no tasks.md to have completed')
  }

  // A count written in prose, beside the file it counts. Feature 005 says 46 where its file holds 54.
  const stated = /\ball\s+(\d+)\s+tasks?\b/i.exec(declared)
  if (stated && feature.hasTasks && Number(stated[1]) !== feature.tasksTotal) {
    return broken(
      `the status line says ${stated[1]} tasks; tasks.md holds ${feature.tasksTotal}`,
    )
  }

  // The skip has to be visible. A feature that is finished but does not say so would otherwise be
  // passed over in silence, which is this tool passing by not looking.
  const outstanding = feature.tasksTotal - feature.tasksDone
  if (!feature.implemented && feature.hasTasks && feature.tasksTotal > 0 && outstanding <= 1) {
    const work =
      outstanding === 0
        ? `all ${feature.tasksTotal} tasks are complete`
        : `${feature.tasksDone} of ${feature.tasksTotal} tasks are complete, the one remaining left open deliberately`
    return broken(
      `${work}, but the status says "${leading}" rather than Implemented, so this feature's ` +
        'documents are not being held to its code',
    )
  }

  return holds()
}
