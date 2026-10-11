package com.gd.skoda.vehicle;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Everything known about the vehicle in one view.
 *
 * @param name         user-defined vehicle name (the model name if none was set)
 * @param licensePlate license plate
 * @param location     last known position, {@link Location#EMPTY} if not delivered
 * @param range        range and energy levels, {@link Range#EMPTY} if not delivered
 * @param status       lock/door/window/light state and odometer, {@link Status#EMPTY} if not delivered
 * @param capturedAt   the most recent vehicle-reported timestamp across all parts
 */
public record VehicleOverview(
        @Nullable String name,
        @Nullable String licensePlate,
        Location location,
        Range range,
        Status status,
        @Nullable Instant capturedAt) {

    public VehicleOverview {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(status, "status");
    }
}
