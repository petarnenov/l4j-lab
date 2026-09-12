package dev.l4jlab.chain.web;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.dataset.SampleDatasetLoader;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.persistence.ChainRunEntity;
import dev.l4jlab.chain.persistence.ChainRunRepository;
import dev.l4jlab.chain.persistence.NodeExecutionEntity;
import dev.l4jlab.chain.persistence.NodeExecutionRepository;
import dev.l4jlab.chain.web.dto.IndicatorView;
import dev.l4jlab.chain.web.dto.NodeView;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import dev.l4jlab.chain.web.dto.RunListResponse;
import dev.l4jlab.chain.web.dto.StartRunRequest;
import dev.l4jlab.chain.web.dto.StartRunResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The run surface. Three reads and one write.
 *
 * <p>{@code @ExecuteOn(BLOCKING)} throughout: every method here touches JDBC, and blocking the
 * Netty event loop would stall every other request for the length of a query.
 */
@Controller("/api/runs")
@ExecuteOn(TaskExecutors.BLOCKING)
public class RunController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ChainRunService runService;
    private final ChainRunRepository runs;
    private final NodeExecutionRepository nodeExecutions;
    private final SampleDatasetLoader dataset;

    public RunController(
            ChainRunService runService,
            ChainRunRepository runs,
            NodeExecutionRepository nodeExecutions,
            SampleDatasetLoader dataset) {
        this.runService = runService;
        this.runs = runs;
        this.nodeExecutions = nodeExecutions;
        this.dataset = dataset;
    }

    /** Starts a run and returns immediately. Two concurrent calls produce two independent runs. */
    @Post
    public HttpResponse<StartRunResponse> start(@Valid @Body StartRunRequest request) {
        if (!dataset.hasCompany(request.companyId())) {
            throw new InvalidSelectionException(
                    "companyId",
                    "No company '" + request.companyId() + "' exists in the catalog. Available: "
                            + String.join(", ", dataset.companyIds()) + ".");
        }
        if (dataset.find(request.companyId(), request.period()).isEmpty()) {
            throw new InvalidSelectionException(
                    "period",
                    "The catalog holds no period '" + request.period() + "' for company '"
                            + request.companyId() + "'. Available: "
                            + String.join(", ", dataset.periodsOf(request.companyId())) + ".");
        }

        UUID runId = runService.start(new Selection(request.companyId(), request.period()));
        return HttpResponse.accepted(URI.create("/api/runs/" + runId))
                .body(new StartRunResponse(runId, RunStatus.PENDING.name()));
    }

    @Get("/{runId}")
    public RunDetailResponse detail(@PathVariable String runId) {
        UUID id = parseUuid(runId);
        ChainRunEntity run = runs.findById(id).orElseThrow(() -> new RunNotFoundException(runId));
        List<NodeExecutionEntity> nodes = nodeExecutions.findByRunIdOrderByPosition(id);

        return new RunDetailResponse(
                run.id(),
                run.companyId(),
                dataset.companyName(run.companyId()),
                run.period(),
                run.status(),
                run.currentNode(),
                run.failedNode(),
                run.failureReason(),
                run.providerMode(),
                run.modelId(),
                run.summaryText(),
                indicatorsOf(nodes),
                run.startedAt(),
                run.endedAt(),
                nodes.stream().map(RunController::toView).toList());
    }

    /** The history list, newest first. Keyset paging, so a concurrent insert cannot skew a page. */
    @Get
    public RunListResponse list(
            @QueryValue(defaultValue = "50") int limit, @Nullable @QueryValue String cursor) {

        int capped = Math.clamp(limit, 1, MAX_LIMIT);
        List<ChainRunEntity> page =
                cursor == null || cursor.isBlank()
                        ? runs.findFirstPage(capped + 1)
                        : decodeCursor(cursor).apply(capped + 1);

        boolean hasMore = page.size() > capped;
        List<ChainRunEntity> visible = hasMore ? page.subList(0, capped) : page;

        List<RunListResponse.RunListEntry> entries =
                visible.stream()
                        .map(
                                run ->
                                        new RunListResponse.RunListEntry(
                                                run.id(),
                                                run.companyId(),
                                                dataset.companyName(run.companyId()),
                                                run.period(),
                                                run.status(),
                                                preview(run.summaryText()),
                                                run.startedAt(),
                                                run.endedAt()))
                        .toList();

        String nextCursor =
                hasMore && !visible.isEmpty()
                        ? encodeCursor(visible.getLast().startedAt(), visible.getLast().id())
                        : null;

        return new RunListResponse(entries, nextCursor);
    }

    private List<IndicatorView> indicatorsOf(List<NodeExecutionEntity> nodes) {
        return nodes.stream()
                .filter(n -> n.position() == 3 && n.succeeded() && n.outputPayload() != null)
                .findFirst()
                .map(RunController::toIndicatorViews)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<IndicatorView> toIndicatorViews(NodeExecutionEntity node) {
        Object payload = node.outputPayload();
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        Object raw = map.get("indicators");
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<String, Object>) entry)
                .map(
                        entry ->
                                new IndicatorView(
                                        asString(entry.get("name")),
                                        asString(entry.get("value")),
                                        asString(entry.get("notApplicableReason")),
                                        entry.get("derivedFrom") instanceof List<?> from
                                                ? from.stream().map(RunController::asString).toList()
                                                : List.of()))
                .toList();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static NodeView toView(NodeExecutionEntity node) {
        return new NodeView(
                node.position(),
                node.nodeName(),
                node.succeeded(),
                node.failureReason(),
                node.inputPayload(),
                node.outputPayload(),
                node.startedAt(),
                node.durationMs(),
                node.modelRequestText(),
                node.modelResponseText(),
                node.inputTokens(),
                node.outputTokens());
    }

    private static String preview(String summary) {
        if (summary == null) {
            return null;
        }
        return summary.length() <= RunListResponse.PREVIEW_LENGTH
                ? summary
                : summary.substring(0, RunListResponse.PREVIEW_LENGTH);
    }

    private UUID parseUuid(String runId) {
        try {
            return UUID.fromString(runId);
        } catch (IllegalArgumentException e) {
            throw new RunNotFoundException(runId);
        }
    }

    private static String encodeCursor(Instant startedAt, UUID id) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((startedAt.toString() + '|' + id).getBytes());
    }

    private java.util.function.IntFunction<List<ChainRunEntity>> decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            int split = decoded.lastIndexOf('|');
            Instant startedAt = Instant.parse(decoded.substring(0, split));
            UUID id = UUID.fromString(decoded.substring(split + 1));
            return limit -> runs.findPageAfter(startedAt, id, limit);
        } catch (RuntimeException e) {
            throw new InvalidSelectionException(
                    "cursor", "The cursor is not one this endpoint issued. Start from the first page.");
        }
    }
}
