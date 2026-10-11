package com.gd.skoda.upstream;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.http.HttpHeaders;

/**
 * Names and lenient parsers of the HTTP headers the MyŠkoda Public API uses.
 *
 * <p>Parsers never throw: a missing or malformed header yields an empty result, because header
 * values are informational and must not turn a successful upstream call into a failure.
 */
public final class UpstreamHeaders {

    /** Request header carrying the API key. Its value must never be logged. */
    public static final String API_KEY = "X-API-Key";
    /** Expiry of the API key, RFC 3339 timestamp. */
    public static final String API_KEY_EXPIRES_AT = "X-API-Key-Expires-At";
    /** Maximum number of requests in the current rate-limit window. */
    public static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";
    /** Requests still available in the current rate-limit window. */
    public static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";
    /** Seconds until the current rate-limit window resets. */
    public static final String RATE_LIMIT_RESET = "RateLimit-Reset";
    /** Seconds (or an HTTP date, RFC 9110) to wait before retrying. */
    public static final String RETRY_AFTER = "Retry-After";

    private UpstreamHeaders() {
    }

    /** Parses an RFC 3339 / ISO 8601 timestamp with offset, e.g. {@code 2026-09-01T00:00:00Z}. */
    public static Optional<Instant> instant(HttpHeaders headers, String name) {
        return value(headers, name).flatMap(value -> {
            try {
                return Optional.of(OffsetDateTime.parse(value).toInstant());
            } catch (DateTimeException ex) {
                return Optional.empty();
            }
        });
    }

    /** Parses a non-negative integer header such as {@code RateLimit-Remaining}. */
    public static OptionalInt nonNegativeInt(HttpHeaders headers, String name) {
        Optional<String> value = value(headers, name);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            int parsed = Integer.parseInt(value.get());
            return parsed >= 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    /**
     * Parses {@code Retry-After} as delay-seconds or as an HTTP date relative to {@code now}
     * (RFC 9110, section 10.2.3). A date in the past yields {@link Duration#ZERO}.
     */
    public static Optional<Duration> retryAfter(HttpHeaders headers, Instant now) {
        Optional<String> value = value(headers, RETRY_AFTER);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        OptionalInt seconds = nonNegativeInt(headers, RETRY_AFTER);
        if (seconds.isPresent()) {
            return Optional.of(Duration.ofSeconds(seconds.getAsInt()));
        }
        try {
            Instant retryAt = ZonedDateTime.parse(value.get(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(now, retryAt);
            return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
        } catch (DateTimeException ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> value(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }
}
