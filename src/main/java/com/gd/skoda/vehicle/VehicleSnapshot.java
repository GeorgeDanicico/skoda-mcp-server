package com.gd.skoda.vehicle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The vehicle as delivered by one upstream call. Internal aggregate that every view
 * ({@link VehicleService#getOverview()}, {@code getLocation()}, ...) is cut from.
 *
 * @param vehicle     the mapped vehicle data
 * @param unavailable every upstream {@code errors[]} entry, in upstream order
 * @param fetchedAt   when the upstream response was received
 */
public record VehicleSnapshot(VehicleOverview vehicle, List<UnavailablePart> unavailable, Instant fetchedAt) {

    public VehicleSnapshot {
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        unavailable = List.copyOf(unavailable);
    }
}
