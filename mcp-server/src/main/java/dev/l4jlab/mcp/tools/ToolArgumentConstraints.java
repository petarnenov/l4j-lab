package dev.l4jlab.mcp.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The argument constraints the Java signature cannot express — declared once (FR-011, research R-002).
 *
 * <p>Feature 007 declared every tool's shape twice by hand: in annotations, which ship, and in JSON
 * under {@code specs/007-mcp-billing-server/contracts/tools/}, which nothing ever read. They drifted
 * to <b>106 differences</b>, and 23 of those were input-schema keywords the committed file carried
 * and the running server did not: enumerations, date formats, bounds, defaults, and {@code integer}
 * widened to {@code number}. That is the constitution's single-source rule broken (finding F-001).
 *
 * <p>R-002 decided the Java is the source and the JSON is generated from it. The obvious place was
 * {@code @ToolArg} — but in micronaut-mcp 2.0.0 it carries only {@code name} and {@code description},
 * and the generator maps Java types crudely: {@code Integer} becomes {@code number}, and a
 * {@code LocalDate} parameter becomes {@code type: object}, which is worse than the {@code String} it
 * would replace. Types were tried first and measured; this is what was left, and no new dependency
 * was added to reach it.
 *
 * <p>So the keywords live here, in Java, once, and are used twice: {@code JsonRpcResponseSerializer}
 * merges them into what {@code tools/list} serves, and {@code McpRequestGate} enforces them on
 * {@code tools/call}. Both readings come from this map. <b>A declared constraint is a validated
 * constraint</b> (FR-013) — restoring {@code enum} to the declaration without enforcing it would
 * replace a lie about what is declared with a lie about what is enforced.
 */
public final class ToolArgumentConstraints {

    private ToolArgumentConstraints() {
    }

    /**
     * One argument's keywords. Every field is optional; only what the generator cannot work out for
     * itself is set, so this never restates a type or a description that {@code @ToolArg} already
     * carries.
     */
    public record Constraint(String type, List<String> allowed, String format,
                             Integer minimum, Integer maximum, Object defaultValue,
                             Integer minLength, Integer maxLength) {

        static Constraint integer(int minimum, int maximum, int defaultValue) {
            return new Constraint("integer", null, null, minimum, maximum, defaultValue, null, null);
        }

        static Constraint integer() {
            return new Constraint("integer", null, null, null, null, null, null, null);
        }

        static Constraint date() {
            return new Constraint("string", null, "date", null, null, null, null, null);
        }

        static Constraint oneOf(List<String> allowed) {
            return new Constraint("string", allowed, null, null, null, null, null, null);
        }

        static Constraint text(Integer minLength, Integer maxLength) {
            return new Constraint("string", null, null, null, null, null, minLength, maxLength);
        }

        /** The keywords, as JSON Schema, for merging into a served declaration. */
        public Map<String, Object> asSchemaKeywords() {
            Map<String, Object> keywords = new LinkedHashMap<>();
            if (type != null) {
                keywords.put("type", type);
            }
            if (allowed != null) {
                keywords.put("enum", allowed);
            }
            if (format != null) {
                keywords.put("format", format);
            }
            if (minimum != null) {
                keywords.put("minimum", minimum);
            }
            if (maximum != null) {
                keywords.put("maximum", maximum);
            }
            if (minLength != null) {
                keywords.put("minLength", minLength);
            }
            if (maxLength != null) {
                keywords.put("maxLength", maxLength);
            }
            if (defaultValue != null) {
                keywords.put("default", defaultValue);
            }
            return keywords;
        }
    }

    private static final List<String> RUN_STATUSES =
        List.of("PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELED");

    private static final Map<String, Map<String, Constraint>> BY_TOOL = Map.of(
        "search_billing_runs", Map.of(
            "status", Constraint.oneOf(RUN_STATUSES),
            "started_from", Constraint.date(),
            "started_to", Constraint.date(),
            "page_size", Constraint.integer(1, 20, 20)),
        "get_run_failures", Map.of(
            "limit", Constraint.integer(1, 50, 50)),
        "post_fee_adjustment", Map.of(
            "operation_id", Constraint.text(8, 128),
            "delta_bps", Constraint.integer(),
            "effective_date", Constraint.date(),
            "reason", Constraint.text(null, 200)),
        "get_billing_run_status", Map.of(),
        "start_billing_run", Map.of());

    public static Map<String, Constraint> forTool(String toolName) {
        return BY_TOOL.getOrDefault(toolName, Map.of());
    }

    /**
     * Checks one call's arguments.
     *
     * @return the sentence to answer with, naming the field, or {@code null} if every argument is
     *     within what the tool declares
     */
    public static String firstViolation(String toolName, Map<String, Object> arguments) {
        for (Map.Entry<String, Constraint> entry : forTool(toolName).entrySet()) {
            Object value = arguments.get(entry.getKey());
            if (value == null) {
                continue;
            }
            String problem = check(entry.getKey(), entry.getValue(), value);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static String check(String field, Constraint constraint, Object value) {
        if (constraint.allowed() != null) {
            if (!(value instanceof String text) || !constraint.allowed().contains(text)) {
                return "%s must be one of %s.".formatted(field, String.join(", ", constraint.allowed()));
            }
        }
        if ("date".equals(constraint.format())) {
            if (!(value instanceof String text) || !isDate(text)) {
                return "%s must be a date in the form YYYY-MM-DD.".formatted(field);
            }
        }
        if ("integer".equals(constraint.type())) {
            if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
                return "%s must be a whole number.".formatted(field);
            }
            long actual = number.longValue();
            if (constraint.minimum() != null && actual < constraint.minimum()) {
                return "%s must be at least %d.".formatted(field, constraint.minimum());
            }
            if (constraint.maximum() != null && actual > constraint.maximum()) {
                return "%s must be at most %d.".formatted(field, constraint.maximum());
            }
        }
        if (constraint.minLength() != null || constraint.maxLength() != null) {
            if (!(value instanceof String text)) {
                return "%s must be text.".formatted(field);
            }
            if (constraint.minLength() != null && text.length() < constraint.minLength()) {
                return "%s must be at least %d characters.".formatted(field, constraint.minLength());
            }
            if (constraint.maxLength() != null && text.length() > constraint.maxLength()) {
                return "%s must be at most %d characters.".formatted(field, constraint.maxLength());
            }
        }
        return null;
    }

    private static boolean isDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
