package dev.l4jlab.chain.model;

import io.micronaut.serde.annotation.Serdeable;

/** Which inference backend a run used. Recorded on every run trace (Principle V). */
@Serdeable
public enum ProviderMode {
    LOCAL,
    CLOUD
}
