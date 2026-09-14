package dev.l4jlab.legacy.security;

import java.util.List;
import java.util.Set;

/**
 * What the caller may see and do, derived from the token's claims and from nothing else (FR-013).
 *
 * <p>The request never widens this. A caller asking for another firm's runs is refused because the
 * scope says so, not because a parameter was validated.
 */
public record CallerScope(String userId, String firmId, String role, Set<String> advisorIds) {

    private static final Set<String> SEES_WHOLE_FIRM = Set.of("FIRM_ADMIN", "OPS");

    public CallerScope(String userId, String firmId, String role, List<String> advisorIds) {
        this(userId, firmId, role, Set.copyOf(advisorIds));
    }

    /** FIRM_ADMIN and OPS see every advisor of their firm; an ADVISOR sees only itself. */
    public boolean seesWholeFirm() {
        return SEES_WHOLE_FIRM.contains(role);
    }

    public boolean mayActFor(String advisorId) {
        return seesWholeFirm() || advisorIds.contains(advisorId);
    }

    /** READ_ONLY exists precisely so that "can read" and "can write" are different questions. */
    public boolean mayWrite() {
        return !"READ_ONLY".equals(role);
    }
}
