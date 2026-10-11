package com.gd.skoda.upstream;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ApiKeyStatusTest {

    private static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final ApiKeyStatus status = new ApiKeyStatus(clock);

    @Test
    void isEmptyBeforeFirstResponse() {
        assertThat(status.current()).isEqualTo(ApiKeyStatus.Snapshot.EMPTY);
        assertThat(status.current().keyValidFor(NOW)).isEmpty();
    }

    @Test
    void recordsAllHeadersAndCallTime() {
        ApiKeyStatus.Snapshot snapshot = status.recordResponse(headers(
                "X-API-Key-Expires-At", "2026-12-01T00:00:00Z",
                "RateLimit-Limit", "20",
                "RateLimit-Remaining", "17",
                "RateLimit-Reset", "1800",
                "Retry-After", "30"));

        assertThat(snapshot).isEqualTo(status.current());
        assertThat(snapshot.keyExpiresAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(snapshot.rateLimitLimit()).isEqualTo(20);
        assertThat(snapshot.rateLimitRemaining()).isEqualTo(17);
        assertThat(snapshot.rateLimitResetAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(snapshot.retryNotBefore()).isEqualTo(NOW.plusSeconds(30));
        assertThat(snapshot.lastCallAt()).isEqualTo(NOW);
        assertThat(snapshot.keyValidFor(NOW)).contains(Duration.between(NOW, Instant.parse("2026-12-01T00:00:00Z")));
    }

    @Test
    void parsesExpiryWithOffset() {
        status.recordResponse(headers("X-API-Key-Expires-At", "2026-12-01T02:00:00+02:00"));

        assertThat(status.current().keyExpiresAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void keepsKnownValuesWhenLaterResponseLacksThemButClearsRetryAfter() {
        status.recordResponse(headers(
                "X-API-Key-Expires-At", "2026-12-01T00:00:00Z",
                "RateLimit-Limit", "20",
                "RateLimit-Remaining", "1",
                "RateLimit-Reset", "60",
                "Retry-After", "60"));
        clock.advance(Duration.ofMinutes(5));

        ApiKeyStatus.Snapshot snapshot = status.recordResponse(headers("RateLimit-Remaining", "19"));

        assertThat(snapshot.keyExpiresAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(snapshot.rateLimitLimit()).isEqualTo(20);
        assertThat(snapshot.rateLimitRemaining()).isEqualTo(19);
        assertThat(snapshot.rateLimitResetAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(snapshot.retryNotBefore()).isNull();
        assertThat(snapshot.lastCallAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void ignoresMalformedHeaders() {
        ApiKeyStatus.Snapshot snapshot = status.recordResponse(headers(
                "X-API-Key-Expires-At", "next tuesday",
                "RateLimit-Limit", "-5",
                "RateLimit-Remaining", "lots",
                "RateLimit-Reset", "",
                "Retry-After", "soon"));

        assertThat(snapshot).isEqualTo(new ApiKeyStatus.Snapshot(null, null, null, null, null, NOW));
    }

    @Test
    void acceptsRetryAfterAsHttpDate() {
        ApiKeyStatus.Snapshot snapshot = status.recordResponse(headers("Retry-After", "Sun, 11 Oct 2026 08:02:00 GMT"));

        assertThat(snapshot.retryNotBefore()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void reportsNegativeValidityOnceExpired() {
        status.recordResponse(headers("X-API-Key-Expires-At", "2026-10-10T08:00:00Z"));

        assertThat(status.current().keyValidFor(NOW)).contains(Duration.ofDays(-1));
    }

    private static HttpHeaders headers(String... namesAndValues) {
        HttpHeaders headers = new HttpHeaders();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            headers.add(namesAndValues[i], namesAndValues[i + 1]);
        }
        return headers;
    }
}
