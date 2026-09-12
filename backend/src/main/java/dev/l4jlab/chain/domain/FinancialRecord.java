package dev.l4jlab.chain.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

/**
 * One company's reported figures for one period, read from the committed sample dataset. Fictional
 * throughout; nothing here is real financial data.
 */
@Serdeable
public record FinancialRecord(
        String companyId,
        String companyName,
        String period,
        BigDecimal revenue,
        BigDecimal costOfGoodsSold,
        BigDecimal netIncome,
        BigDecimal currentAssets,
        BigDecimal currentLiabilities,
        BigDecimal totalDebt,
        BigDecimal equity) {}
