package com.gd.skoda.vehicle;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Driving range and energy levels. Every component is {@code null} while unknown; values are rounded
 * to whole numbers.
 *
 * <p>Built from {@code fuelStatus}. For a vehicle without fuel status but with {@code charging} data
 * (battery-electric), the primary engine range and the total range are derived from the battery.
 *
 * @param carType           vehicle type ({@code fuelStatus.carType})
 * @param totalRangeKm      total remaining range
 * @param primaryEngine     range of the primary engine
 * @param secondaryEngine   range of the secondary engine (hybrids)
 * @param adBlueRangeKm     AdBlue range, diesel vehicles only
 * @param batterySocPercent battery state of charge, from {@code charging} or from an electric engine range
 * @param capturedAt        when the vehicle reported the data
 */
public record Range(
        @Nullable CarType carType,
        @Nullable Integer totalRangeKm,
        @Nullable EngineRange primaryEngine,
        @Nullable EngineRange secondaryEngine,
        @Nullable Integer adBlueRangeKm,
        @Nullable Integer batterySocPercent,
        @Nullable Instant capturedAt) {

    /** No range data at all. */
    public static final Range EMPTY = new Range(null, null, null, null, null, null, null);
}
