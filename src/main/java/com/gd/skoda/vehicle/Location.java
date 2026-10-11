package com.gd.skoda.vehicle;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Last known position of the vehicle. Every component is {@code null} while unknown; coordinates and
 * address are only reported while the vehicle is parked.
 *
 * @param state            parked or in motion
 * @param latitude         WGS84 latitude
 * @param longitude        WGS84 longitude
 * @param formattedAddress "street number, zip city, country", if the upstream API could resolve it
 * @param capturedAt       when the vehicle reported the position (the Public API v1.1.0 does not
 *                         send a timestamp for the parking position, so this is currently always
 *                         {@code null})
 */
public record Location(
        @Nullable ParkingState state,
        @Nullable Double latitude,
        @Nullable Double longitude,
        @Nullable String formattedAddress,
        @Nullable Instant capturedAt) {

    /** No location data at all. */
    public static final Location EMPTY = new Location(null, null, null, null, null);
}
