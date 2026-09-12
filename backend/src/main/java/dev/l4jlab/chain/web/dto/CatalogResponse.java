package dev.l4jlab.chain.web.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** The companies and periods a learner may select. Backs the launcher's two dropdowns. */
@Serdeable
public record CatalogResponse(List<CompanyEntry> companies) {

    @Serdeable
    public record CompanyEntry(String companyId, String companyName, List<String> periods) {}
}
