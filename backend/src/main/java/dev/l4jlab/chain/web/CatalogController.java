package dev.l4jlab.chain.web;

import dev.l4jlab.chain.dataset.SampleDatasetLoader;
import dev.l4jlab.chain.web.dto.CatalogResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;

/** The selectable companies and periods. Read straight from the committed dataset, so it is stable. */
@Controller("/api/catalog")
@ExecuteOn(TaskExecutors.BLOCKING)
public class CatalogController {

    private final SampleDatasetLoader dataset;

    public CatalogController(SampleDatasetLoader dataset) {
        this.dataset = dataset;
    }

    @Get
    public CatalogResponse catalog() {
        List<CatalogResponse.CompanyEntry> companies =
                dataset.companyIds().stream()
                        .map(id -> new CatalogResponse.CompanyEntry(
                                id, dataset.companyName(id), dataset.periodsOf(id)))
                        .toList();
        return new CatalogResponse(companies);
    }
}
