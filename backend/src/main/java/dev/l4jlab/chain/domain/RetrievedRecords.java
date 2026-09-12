package dev.l4jlab.chain.domain;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Boundary 2 to 3. Produced by RetrieveRecords. {@code prior} is absent for a company's first
 * period, which is the only case where revenue growth has no defined answer.
 */
@Serdeable
public record RetrievedRecords(
        @NotBlank String companyId,
        @NotBlank String companyName,
        @Pattern(regexp = "\\d{4}-Q[1-4]") String period,
        @NotNull FinancialRecord current,
        @Nullable FinancialRecord prior) {}
