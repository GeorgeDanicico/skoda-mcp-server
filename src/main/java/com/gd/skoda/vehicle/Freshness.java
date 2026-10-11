package com.gd.skoda.vehicle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * How current a {@link VehicleData} result is and what it is missing.
 *
 * @param fetchedAt   when the data was received from the upstream API (not when the vehicle captured
 *                    it; see the {@code capturedAt} of each record)
 * @param stale       {@code true} if the cache could not be refreshed (rate limit almost used up,
 *                    upstream 429, 5xx or timeout) and older data is served instead
 * @param unavailable the parts relevant to this result that the upstream API did not deliver
 */
public record Freshness(Instant fetchedAt, boolean stale, List<UnavailablePart> unavailable) {

    public Freshness {
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        unavailable = List.copyOf(unavailable);
    }
}
