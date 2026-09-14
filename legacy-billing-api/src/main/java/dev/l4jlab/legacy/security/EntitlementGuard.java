package dev.l4jlab.legacy.security;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

/**
 * The one place an entitlement decision is made (FR-014, FR-023).
 *
 * <p>Every refusal is a 403 raised <em>before</em> existence is checked. That is deliberate: a
 * caller must not be able to tell a firm's real run id from a made-up one by comparing 403 with 404.
 * The MCP server turns either into the same short "no access" tool error.
 */
@Singleton
public class EntitlementGuard {

    public void requireOwnFirm(CallerScope scope, String firmId) {
        if (!scope.firmId().equals(firmId)) {
            throw refuse();
        }
    }

    public void requireMayActFor(CallerScope scope, String advisorId) {
        if (!scope.mayActFor(advisorId)) {
            throw refuse();
        }
    }

    public void requireWrite(CallerScope scope) {
        if (!scope.mayWrite()) {
            throw refuse();
        }
    }

    /**
     * Deliberately uniform and contentless. Anything more specific would leak the shape of the data
     * to a caller who is not entitled to know it exists.
     */
    private static HttpStatusException refuse() {
        return new HttpStatusException(HttpStatus.FORBIDDEN, "Not entitled");
    }
}
