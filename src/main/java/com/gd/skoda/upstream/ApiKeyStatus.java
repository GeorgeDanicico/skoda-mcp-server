package com.gd.skoda.upstream;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

/**
 * Thread-safe record of what the MyŠkoda Public API last told us about the API key and its quota.
 *
 * <p>Updated by {@link ApiKeyInterceptor} from the headers of every upstream response (success or
 * error). Readers get an immutable {@link Snapshot}.
 */
public final class ApiKeyStatus {

    private final Clock clock;
    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.EMPTY);

    public ApiKeyStatus(Clock clock) {
        this.clock = clock;
    }

    /** The latest snapshot; {@link Snapshot#EMPTY} until the first upstream response. */
    public Snapshot current() {
        return current.get();
    }

    /**
     * Records the headers of an upstream response received now.
     *
     * <p>{@code X-API-Key-Expires-At} and {@code RateLimit-*} values that are missing or malformed
     * keep their previous value (for example, 5xx responses may not carry them). {@code Retry-After}
     * applies to the response it came with only, so it is cleared when absent.
     *
     * @return the updated snapshot
     */
    public Snapshot recordResponse(HttpHeaders headers) {
        Instant now = clock.instant();
        Optional<Instant> expiresAt = UpstreamHeaders.instant(headers, UpstreamHeaders.API_KEY_EXPIRES_AT);
        OptionalInt limit = UpstreamHeaders.nonNegativeInt(headers, UpstreamHeaders.RATE_LIMIT_LIMIT);
        OptionalInt remaining = UpstreamHeaders.nonNegativeInt(headers, UpstreamHeaders.RATE_LIMIT_REMAINING);
        OptionalInt resetSeconds = UpstreamHeaders.nonNegativeInt(headers, UpstreamHeaders.RATE_LIMIT_RESET);
        Optional<Duration> retryAfter = UpstreamHeaders.retryAfter(headers, now);

        return current.updateAndGet(previous -> new Snapshot(
                expiresAt.orElse(previous.keyExpiresAt()),
                limit.isPresent() ? Integer.valueOf(limit.getAsInt()) : previous.rateLimitLimit(),
                remaining.isPresent() ? Integer.valueOf(remaining.getAsInt()) : previous.rateLimitRemaining(),
                resetSeconds.isPresent() ? now.plusSeconds(resetSeconds.getAsInt()) : previous.rateLimitResetAt(),
                retryAfter.map(now::plus).orElse(null),
                now));
    }

    /**
     * Immutable view of the API key and quota state. Every component is {@code null} while unknown.
     *
     * @param keyExpiresAt       when the API key expires ({@code X-API-Key-Expires-At})
     * @param rateLimitLimit     requests allowed per rate-limit window ({@code RateLimit-Limit})
     * @param rateLimitRemaining requests left in the current window ({@code RateLimit-Remaining})
     * @param rateLimitResetAt   when the current window resets ({@code RateLimit-Reset} relative to the response)
     * @param retryNotBefore     earliest retry time requested by the last response ({@code Retry-After}),
     *                           {@code null} if the last response did not ask to wait
     * @param lastCallAt         when the last upstream response was received
     */
    public record Snapshot(
            @Nullable Instant keyExpiresAt,
            @Nullable Integer rateLimitLimit,
            @Nullable Integer rateLimitRemaining,
            @Nullable Instant rateLimitResetAt,
            @Nullable Instant retryNotBefore,
            @Nullable Instant lastCallAt) {

        /** State before any upstream call. */
        public static final Snapshot EMPTY = new Snapshot(null, null, null, null, null, null);

        /** Time left until the key expires, negative once expired; empty while the expiry is unknown. */
        public Optional<Duration> keyValidFor(Instant now) {
            return Optional.ofNullable(keyExpiresAt).map(expiry -> Duration.between(now, expiry));
        }
    }
}
