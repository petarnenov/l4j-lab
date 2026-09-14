package dev.l4jlab.issuer;

import java.util.List;
import java.util.Map;

/**
 * The fixture principals (contracts/token-issuer.md), matching the seeded legacy data.
 *
 * <p>Six of them, chosen to make the entitlement scenarios expressible: two advisors in one firm so
 * an ADVISOR's narrow view is visibly narrower than a FIRM_ADMIN's, an OPS and a READ_ONLY to
 * exercise the other two roles, and an admin of a second firm so cross-firm refusal has something
 * to refuse.
 */
public final class Principals {

    /** A principal as the issuer knows it, before it becomes claims. */
    public record Fixture(String userId, String firmId, String role, List<String> advisorIds) {
    }

    private static final Map<String, Fixture> BY_NAME = Map.of(
        "advisor-alpha-101", new Fixture("usr-101", "firm-alpha", "ADVISOR", List.of("adv-101")),
        "advisor-alpha-102", new Fixture("usr-102", "firm-alpha", "ADVISOR", List.of("adv-102")),
        "admin-alpha", new Fixture("usr-900", "firm-alpha", "FIRM_ADMIN", List.of("adv-101", "adv-102")),
        "ops-alpha", new Fixture("usr-901", "firm-alpha", "OPS", List.of("adv-101", "adv-102")),
        "readonly-alpha", new Fixture("usr-902", "firm-alpha", "READ_ONLY", List.of("adv-101", "adv-102")),
        "admin-beta", new Fixture("usr-800", "firm-beta", "FIRM_ADMIN", List.of("adv-201")));

    private Principals() {
    }

    public static Fixture byName(String name) {
        Fixture fixture = BY_NAME.get(name);
        if (fixture == null) {
            throw new IllegalArgumentException("Unknown fixture principal: " + name);
        }
        return fixture;
    }

    public static boolean exists(String name) {
        return BY_NAME.containsKey(name);
    }
}
