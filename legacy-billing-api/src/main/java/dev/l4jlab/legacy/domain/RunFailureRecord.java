package dev.l4jlab.legacy.domain;

import io.micronaut.core.annotation.Introspected;

/**
 * One household a failed run could not process, with the reason (FR-019).
 *
 * <p>{@code @Introspected} because this is the projection of a native query, not a mapped entity.
 * Without it Micronaut Data cannot build the record and the symptom is a 500 that names nothing.
 */
@Introspected
public record RunFailureRecord(String householdId, String householdName, String cause) {
}
