package dev.l4jlab.chain.web.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record StartRunResponse(UUID runId, String status) {}
