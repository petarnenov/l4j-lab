package dev.l4jlab.mcp;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A stand-in for the legacy API and the issuer, for the deterministic suite only.
 *
 * <p>Constitution Principle IV: these tests run in-process with no network and no credential, so the
 * system of record cannot be a container here. What this stub does <em>not</em> do is soften
 * anything the tests assert — it records every inbound Authorization header so the
 * "never forwards the token" property is still checked, and it returns the same status codes the
 * real API returns so the error translation is exercised for real. The real topology is covered by
 * {@code topologyTest}.
 */
@Requires(env = "test")
@Singleton
@Controller
public class StubLegacyApi {

    /** What the stub was asked, so a test can assert on what actually travelled. */
    public record Received(String path, String authorization, String traceparent) {
    }

    @Serdeable
    public record Page<T>(List<T> items, int totalCount) {
    }

    @Serdeable
    public record Run(String runId, String firmId, String executedByAdvisorId, String status,
                      @Nullable String phase, int accountsProcessed, int accountsTotal,
                      @Nullable String failureReason, String startedAt) {
    }

    @Serdeable
    public record Failure(String householdId, String householdName, String cause) {
    }

    @Serdeable
    public record ExchangeResponse(String token, long expiresInSeconds) {
    }

    @Serdeable
    public record StartRunRequest(String firmId, String executedByAdvisorId) {
    }

    @Serdeable
    public record AdjustmentRequest(String accountId, int deltaBps, String effectiveDate,
                                    @Nullable String reason) {
    }

    @Serdeable
    public record AdjustmentResult(String legacyReferenceId, String accountId, int deltaBps,
                                   String effectiveDate, int newFeeBps) {
    }

    private final ConcurrentLinkedQueue<Received> received = new ConcurrentLinkedQueue<>();

    /** Set by a test to make the next legacy call fail in a particular way. */
    private volatile int forcedStatus;

    public List<Received> received() {
        return List.copyOf(received);
    }

    public void clear() {
        received.clear();
        adjustments.clear();
        forcedStatus = 0;
        runStatus = "RUNNING";
        runPhase = "FEE_CALC";
    }

    /** Makes every subsequent legacy call return this status, for the error-translation tests. */
    public void forceStatus(int status) {
        this.forcedStatus = status;
    }

    // ---------- the issuer, reduced to what the MCP server asks of it ----------

    @Get("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return TestKeys.jwks().toJSONObject();
    }

    @Post("/dev/exchange")
    public ExchangeResponse exchange(@Body Map<String, Object> request) {
        // Mints a genuinely different token for the legacy audience, so a test that asserts the
        // inbound token never travels has something real to distinguish it from.
        return new ExchangeResponse(
            TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_LEGACY), 300);
    }

    // ---------- the legacy API ----------

    @Get("/api/v1/billing-runs")
    public HttpResponse<?> search(@QueryValue String firmId,
                                  @QueryValue @Nullable String status,
                                  @QueryValue @Nullable String startedFrom,
                                  @QueryValue(defaultValue = "0") int offset,
                                  @QueryValue(defaultValue = "20") int limit,
                                  @Header("Authorization") @Nullable String authorization,
                                  @Header("traceparent") @Nullable String traceparent) {
        record("/api/v1/billing-runs", authorization, traceparent);
        HttpResponse<?> forced = forced();
        if (forced != null) {
            return forced;
        }
        List<Run> all = new ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            all.add(new Run("run-a%03d".formatted(i), firmId, "adv-101",
                status == null ? "COMPLETED" : status, null, 12, 12, null,
                "2026-08-%02dT09:00:00Z".formatted(i)));
        }
        // The stub honours startedFrom so a search matching nothing can be reproduced here rather
        // than only against the running stack. The seeded runs are all in August 2026, so any later
        // date is an empty result — which is the query that used to answer HTTP 500 (feature 010).
        if (startedFrom != null && startedFrom.compareTo("2026-09-01") >= 0) {
            all.clear();
        }
        int from = Math.min(offset, all.size());
        int to = Math.min(from + limit, all.size());
        return HttpResponse.ok(new Page<>(all.subList(from, to), all.size()));
    }

    @Get("/api/v1/billing-runs/{runId}")
    public HttpResponse<?> one(@PathVariable String runId,
                               @Header("Authorization") @Nullable String authorization,
                               @Header("traceparent") @Nullable String traceparent) {
        record("/api/v1/billing-runs/" + runId, authorization, traceparent);
        HttpResponse<?> forced = forced();
        if (forced != null) {
            return forced;
        }
        if ("run-started".equals(runId)) {
            return HttpResponse.ok(new Run(runId, "firm-alpha", "adv-101", runStatus, runPhase,
                "COMPLETED".equals(runStatus) ? 24 : 6, 24, null, "2026-09-13T10:00:00Z"));
        }
        boolean failed = runId.endsWith("-failed");
        return HttpResponse.ok(new Run(runId, "firm-alpha", "adv-101",
            failed ? "FAILED" : "RUNNING", failed ? null : "FEE_CALC", 6, 12,
            failed ? "Three households could not be priced" : null, "2026-08-01T09:00:00Z"));
    }

    @Get("/api/v1/billing-runs/{runId}/failures")
    public HttpResponse<?> failures(@PathVariable String runId,
                                    @QueryValue(defaultValue = "50") int limit,
                                    @Header("Authorization") @Nullable String authorization,
                                    @Header("traceparent") @Nullable String traceparent) {
        record("/api/v1/billing-runs/" + runId + "/failures", authorization, traceparent);
        HttpResponse<?> forced = forced();
        if (forced != null) {
            return forced;
        }
        List<Failure> all = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            all.add(new Failure("hh-%03d".formatted(i), "Household %03d".formatted(i),
                "Missing market value on the valuation date"));
        }
        return HttpResponse.ok(new Page<>(all.subList(0, Math.min(limit, all.size())), all.size()));
    }

    /** Every adjustment the stub was asked to apply, so a test can count them. */
    private final ConcurrentLinkedQueue<AdjustmentRequest> adjustments = new ConcurrentLinkedQueue<>();

    public List<AdjustmentRequest> adjustments() {
        return List.copyOf(adjustments);
    }

    @Post("/api/v1/fee-adjustments")
    public HttpResponse<?> adjust(@Body AdjustmentRequest request,
                                  @Header("Authorization") @Nullable String authorization,
                                  @Header("traceparent") @Nullable String traceparent) {
        record("/api/v1/fee-adjustments", authorization, traceparent);
        HttpResponse<?> forced = forced();
        if (forced != null) {
            return forced;
        }
        adjustments.add(request);
        return HttpResponse.created(new AdjustmentResult(
            "adj-" + adjustments.size(), request.accountId(), request.deltaBps(),
            request.effectiveDate(), 100 + request.deltaBps()));
    }

    /**
     * The status a started run reports on the next poll. A test drives the simulation by hand
     * instead of waiting 30 seconds for it — the real timing is asserted in the legacy API's own
     * suite against an injected clock (T099).
     */
    private volatile String runStatus = "RUNNING";
    private volatile String runPhase = "FEE_CALC";

    public void setRunState(String status, String phase) {
        this.runStatus = status;
        this.runPhase = phase;
    }

    @Post("/api/v1/billing-runs")
    public HttpResponse<?> start(@Body StartRunRequest request,
                                 @Header("Authorization") @Nullable String authorization,
                                 @Header("traceparent") @Nullable String traceparent) {
        record("/api/v1/billing-runs", authorization, traceparent);
        HttpResponse<?> forced = forced();
        if (forced != null) {
            return forced;
        }
        setRunState("RUNNING", "DATA_COLLECTION");
        return HttpResponse.created(new Run("run-started", request.firmId(),
            request.executedByAdvisorId(), "PENDING", "DATA_COLLECTION", 0, 24, null,
            "2026-09-13T10:00:00Z"));
    }

    @Post("/api/v1/billing-runs/{runId}/cancel")
    public HttpResponse<?> cancel(@PathVariable String runId,
                                  @Header("Authorization") @Nullable String authorization,
                                  @Header("traceparent") @Nullable String traceparent) {
        record("/api/v1/billing-runs/" + runId + "/cancel", authorization, traceparent);
        HttpResponse<?> forced = forced();
        if (forced != null) {
            return forced;
        }
        // Cooperative: a terminal run keeps its status and the request still succeeds (FR-031).
        if (!"COMPLETED".equals(runStatus) && !"FAILED".equals(runStatus)) {
            setRunState("CANCELED", null);
        }
        return HttpResponse.ok(new Run(runId, "firm-alpha", "adv-101", runStatus, runPhase,
            6, 24, null, "2026-09-13T10:00:00Z"));
    }

    private HttpResponse<?> forced() {
        int status = forcedStatus;
        if (status == 0) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "forced by the test");
        return HttpResponse.status(io.micronaut.http.HttpStatus.valueOf(status)).body(body);
    }

    private void record(String path, String authorization, String traceparent) {
        received.add(new Received(path, authorization, traceparent));
    }
}
