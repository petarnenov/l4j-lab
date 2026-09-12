package dev.l4jlab.chain.web.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record StartRunRequest(@NotBlank String companyId, @NotBlank String period) {}
