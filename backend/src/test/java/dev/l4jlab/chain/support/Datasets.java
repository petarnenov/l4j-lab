package dev.l4jlab.chain.support;

import dev.l4jlab.chain.dataset.SampleDatasetLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.serde.ObjectMapper;

/**
 * Builds the real dataset loader over the committed JSON without starting an application context.
 *
 * <p>Principle IV requires tests that do not exercise the model to run with no credential and no
 * network. Booting a context to read a file would also have dragged in a datasource and, with it, a
 * dependency on Docker.
 */
public final class Datasets {

    private Datasets() {}

    public static SampleDatasetLoader committed() {
        return new SampleDatasetLoader(
                ClassPathResourceLoader.defaultLoader(Datasets.class.getClassLoader()),
                ObjectMapper.getDefault());
    }
}
