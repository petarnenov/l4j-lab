package dev.l4jlab.chain.domain;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * Boundary 1 to 2. Produced by PrepareRequest from the learner's selection.
 *
 * <p>{@code @Serdeable} because this record is written verbatim into a node_execution JSONB payload
 * column. Micronaut Serde refuses to serialize a type it has no introspection for, and the omission
 * compiles and passes every unit test before failing on the first real run.
 */
@Serdeable
public record ChainRequest(
        @NonNull @NotBlank @Pattern(regexp = "[a-z0-9-]{3,64}") String companyId,
        @NonNull @NotBlank @Pattern(regexp = "\\d{4}-Q[1-4]") String period,
        @NonNull @NotNull Instant requestedAt) {}
