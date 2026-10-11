package com.gd.skoda.vehicle;

import com.gd.skoda.upstream.ApiKeyStatus;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * What the upstream API last reported about the API key and its quota. Every component is
 * {@code null} until the first upstream response carried it.
 *
 * @param keyExpiresAt       when the API key expires
 * @param daysUntilExpiry    whole days until expiry (rounded down), negative once expired
 * @param rateLimitLimit     requests allowed per window
 * @param rateLimitRemaining requests left in the window, as of the last upstream response (the
 *                           window may have reset since, see {@code rateLimitResetAt})
 * @param rateLimitResetAt   when the window resets
 * @param lastUpstreamCallAt when the last upstream response was received
 */
public record ApiKeyInfo(
        @Nullable Instant keyExpiresAt,
        @Nullable Long daysUntilExpiry,
        @Nullable Integer rateLimitLimit,
        @Nullable Integer rateLimitRemaining,
        @Nullable Instant rateLimitResetAt,
        @Nullable Instant lastUpstreamCallAt) {

    /** Builds the info from an {@link ApiKeyStatus} snapshot as seen at {@code now}. */
    public static ApiKeyInfo from(ApiKeyStatus.Snapshot status, Instant now) {
        Long days = status.keyValidFor(now).map(ApiKeyInfo::wholeDays).orElse(null);
        return new ApiKeyInfo(status.keyExpiresAt(), days, status.rateLimitLimit(), status.rateLimitRemaining(),
                status.rateLimitResetAt(), status.lastCallAt());
    }

    private static long wholeDays(Duration validFor) {
        // floor, so a key expiring in 6 days 23 hours reports 6 and one that expired an hour ago reports -1
        return Math.floorDiv(validFor.toSeconds(), Duration.ofDays(1).toSeconds());
    }
}
