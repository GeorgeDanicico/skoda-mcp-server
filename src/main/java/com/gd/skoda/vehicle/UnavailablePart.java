package com.gd.skoda.vehicle;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A part of the vehicle data the upstream API did not deliver, parsed from one {@code errors[]} entry.
 *
 * @param part        the affected part ({@link DataPart#UNKNOWN} for unrecognised error types)
 * @param reason      why it is missing ({@link UnavailabilityReason#UNKNOWN} for unrecognised error types)
 * @param type        the upstream error type as received, e.g. {@code PARKING_POSITION_UNAVAILABLE}
 * @param description the upstream description, if any
 */
public record UnavailablePart(
        DataPart part,
        UnavailabilityReason reason,
        @Nullable String type,
        @Nullable String description) {

    public UnavailablePart {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(reason, "reason");
    }
}
