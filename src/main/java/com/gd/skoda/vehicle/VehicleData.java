package com.gd.skoda.vehicle;

import java.util.Objects;

/**
 * A vehicle view plus its {@link Freshness}. Every {@link VehicleService} read returns one.
 *
 * @param data      the view, never {@code null} (missing parts are empty records, e.g. {@link Location#EMPTY})
 * @param freshness when the data was fetched, whether it is stale, and what is missing
 * @param <T>       the view type
 */
public record VehicleData<T>(T data, Freshness freshness) {

    public VehicleData {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(freshness, "freshness");
    }
}
