package com.gd.skoda.vehicle;

import org.jspecify.annotations.Nullable;

/**
 * Range of one engine (combustion or electric). Every component is {@code null} while unknown.
 * Values are rounded to whole numbers.
 *
 * @param engineType       fuel or electric
 * @param fuelLevelPercent fuel tank level, combustion engines only
 * @param socPercent       battery state of charge, electric engines only
 * @param remainingRangeKm remaining range of this engine
 */
public record EngineRange(
        @Nullable EngineType engineType,
        @Nullable Integer fuelLevelPercent,
        @Nullable Integer socPercent,
        @Nullable Integer remainingRangeKm) {
}
