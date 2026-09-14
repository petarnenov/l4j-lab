package dev.l4jlab.legacy.domain;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

/** An account and the fee a fee adjustment moves. Basis points, so the arithmetic stays exact. */
@MappedEntity(value = "account", schema = "legacy_billing")
public record AccountRecord(
    @Id @MappedProperty("account_id") String accountId,
    @MappedProperty("household_id") String householdId,
    @MappedProperty("current_fee_bps") int currentFeeBps) {
}
