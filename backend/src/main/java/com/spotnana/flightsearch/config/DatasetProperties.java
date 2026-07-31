package com.spotnana.flightsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Where the schedule is read from.
 *
 * <p>Typed as a Spring {@link Resource} so the same property accepts {@code classpath:} for
 * the packaged dataset and {@code file:} for an override mounted into a container or
 * pointed at a fixture in a test.
 *
 * @param location defaults to the packaged {@code flights.json}
 */
@ConfigurationProperties(prefix = "skypath.dataset")
public record DatasetProperties(Resource location) {}
