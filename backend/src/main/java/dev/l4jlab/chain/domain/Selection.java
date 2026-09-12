package dev.l4jlab.chain.domain;

import io.micronaut.serde.annotation.Serdeable;

/** What the learner picked. The input to the first node, and the only thing they supply. */
@Serdeable
public record Selection(String companyId, String period) {}
