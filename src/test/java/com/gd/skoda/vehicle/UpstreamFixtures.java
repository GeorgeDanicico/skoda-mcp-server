package com.gd.skoda.vehicle;

import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import tools.jackson.databind.json.JsonMapper;

/** Loads the upstream JSON fixtures from {@code src/test/resources/upstream} into generated models. */
final class UpstreamFixtures {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private UpstreamFixtures() {
    }

    static VehicleResponse full() {
        return load("vehicle-full.json");
    }

    static VehicleResponse partial() {
        return load("vehicle-partial.json");
    }

    static VehicleResponse load(String name) {
        try (InputStream in = UpstreamFixtures.class.getResourceAsStream("/upstream/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("fixture not found: " + name);
            }
            return JSON.readValue(in, VehicleResponse.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
