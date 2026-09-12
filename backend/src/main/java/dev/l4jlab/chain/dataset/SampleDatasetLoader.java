package dev.l4jlab.chain.dataset;

import dev.l4jlab.chain.domain.FinancialRecord;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the committed sample dataset once at startup and holds it in memory. Read-only; nothing
 * writes back to the file.
 *
 * <p>{@code @Context} so the validations below run during context initialisation. A duplicate or a
 * gap in the periods fails the boot with a message naming the offender, rather than surfacing as a
 * confusing retrieval failure on some later run.
 */
@Context
public class SampleDatasetLoader {

    private static final String RESOURCE = "classpath:data/companies.json";

    private final Map<String, FinancialRecord> byKey = new LinkedHashMap<>();
    private final Map<String, List<String>> periodsByCompany = new LinkedHashMap<>();
    private final Map<String, String> namesByCompany = new LinkedHashMap<>();

    public SampleDatasetLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        Dataset dataset = read(resourceLoader, objectMapper);
        index(dataset.records());
        validateContiguousPeriods();
    }

    private Dataset read(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        Optional<InputStream> stream = resourceLoader.getResourceAsStream(RESOURCE);
        if (stream.isEmpty()) {
            throw new IllegalStateException(
                    "The sample dataset is missing from the build. Expected " + RESOURCE);
        }
        try (InputStream in = stream.get()) {
            return objectMapper.readValue(in.readAllBytes(), Dataset.class);
        } catch (IOException e) {
            throw new IllegalStateException("The sample dataset at " + RESOURCE + " could not be read", e);
        }
    }

    private void index(List<FinancialRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalStateException("The sample dataset holds no records");
        }
        for (FinancialRecord record : records) {
            String key = key(record.companyId(), record.period());
            FinancialRecord existing = byKey.put(key, record);
            if (existing != null) {
                throw new IllegalStateException(
                        "The sample dataset holds a duplicate record for company '"
                                + record.companyId() + "' period '" + record.period() + "'");
            }
            periodsByCompany
                    .computeIfAbsent(record.companyId(), c -> new ArrayList<>())
                    .add(record.period());
            namesByCompany.putIfAbsent(record.companyId(), record.companyName());
        }
        periodsByCompany.values().forEach(periods -> periods.sort(Comparator.naturalOrder()));
    }

    /**
     * Every company must have an unbroken run of quarters, so that prior-period lookup has a
     * defined answer for every period except the first. Without this, revenue growth would be
     * silently not-applicable in the middle of a series and nobody could tell whether that was the
     * data or a bug.
     */
    private void validateContiguousPeriods() {
        periodsByCompany.forEach((companyId, periods) -> {
            for (int i = 1; i < periods.size(); i++) {
                String expected = nextPeriod(periods.get(i - 1));
                if (!expected.equals(periods.get(i))) {
                    throw new IllegalStateException(
                            "The sample dataset has a gap for company '" + companyId + "': "
                                    + periods.get(i - 1) + " is followed by " + periods.get(i)
                                    + ", expected " + expected);
                }
            }
        });
    }

    static String nextPeriod(String period) {
        int year = Integer.parseInt(period.substring(0, 4));
        int quarter = Integer.parseInt(period.substring(6));
        return quarter == 4 ? (year + 1) + "-Q1" : year + "-Q" + (quarter + 1);
    }

    static String previousPeriod(String period) {
        int year = Integer.parseInt(period.substring(0, 4));
        int quarter = Integer.parseInt(period.substring(6));
        return quarter == 1 ? (year - 1) + "-Q4" : year + "-Q" + (quarter - 1);
    }

    public Optional<FinancialRecord> find(String companyId, String period) {
        return Optional.ofNullable(byKey.get(key(companyId, period)));
    }

    @Nullable
    public FinancialRecord findPrior(String companyId, String period) {
        return byKey.get(key(companyId, previousPeriod(period)));
    }

    public boolean hasCompany(String companyId) {
        return periodsByCompany.containsKey(companyId);
    }

    public List<String> periodsOf(String companyId) {
        return List.copyOf(periodsByCompany.getOrDefault(companyId, List.of()));
    }

    public List<String> companyIds() {
        return List.copyOf(periodsByCompany.keySet());
    }

    public String companyName(String companyId) {
        return namesByCompany.get(companyId);
    }

    public int size() {
        return byKey.size();
    }

    private static String key(String companyId, String period) {
        return companyId + '@' + period;
    }

    @Serdeable
    record Dataset(List<FinancialRecord> records) {}
}
