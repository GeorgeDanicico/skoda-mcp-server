package com.gd.skoda.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gd.skoda.support.MutableClock;
import com.gd.skoda.upstream.ApiKeyExpiredException;
import com.gd.skoda.upstream.ApiKeyInvalidException;
import com.gd.skoda.upstream.ApiKeyNotAuthorizedException;
import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.upstream.SkodaApiException;
import com.gd.skoda.upstream.UnexpectedUpstreamResponseException;
import com.gd.skoda.upstream.UpstreamHeaders;
import com.gd.skoda.upstream.UpstreamUnavailableException;
import com.gd.skoda.upstream.VehicleNotFoundException;
import com.gd.skoda.upstream.publicapi.model.VehiclePart;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class SnapshotCacheTest {

    private static final Instant START = Instant.parse("2026-10-11T08:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(START);
    private final ApiKeyStatus apiKeyStatus = new ApiKeyStatus(clock);
    private final FakeUpstream upstream = new FakeUpstream();
    private final SnapshotCache cache =
            new SnapshotCache(upstream.client, new VehicleSnapshotMapper(), apiKeyStatus, clock, TTL);

    @Test
    void requestsAllPartsInOneCall() {
        cache.get();

        assertThat(upstream.lastInclude()).containsExactly(VehiclePart.INFO, VehiclePart.STATUS,
                VehiclePart.FUEL_STATUS, VehiclePart.ODOMETER, VehiclePart.PARKING_POSITION, VehiclePart.CHARGING);
    }

    @Test
    void servesCachedSnapshotWithinTtl() {
        SnapshotCache.CachedSnapshot first = cache.get();
        clock.advance(TTL.minusSeconds(1));
        SnapshotCache.CachedSnapshot second = cache.get();

        assertThat(upstream.calls()).isEqualTo(1);
        assertThat(second.snapshot()).isSameAs(first.snapshot());
        assertThat(second.stale()).isFalse();
        assertThat(first.snapshot().fetchedAt()).isEqualTo(START);
    }

    @Test
    void refreshesOnceTtlHasPassed() {
        SnapshotCache.CachedSnapshot first = cache.get();
        clock.advance(TTL);
        SnapshotCache.CachedSnapshot second = cache.get();

        assertThat(upstream.calls()).isEqualTo(2);
        assertThat(second.snapshot()).isNotSameAs(first.snapshot());
        assertThat(second.snapshot().fetchedAt()).isEqualTo(START.plus(TTL));
        assertThat(second.stale()).isFalse();
    }

    @Test
    void concurrentCallersShareOneUpstreamCall() throws Exception {
        int callers = 20;
        CountDownLatch allStarted = new CountDownLatch(callers);
        CountDownLatch release = new CountDownLatch(1);
        upstream.respondWith(() -> {
            await(release);
            return UpstreamFixtures.full();
        });

        List<Future<SnapshotCache.CachedSnapshot>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    allStarted.countDown();
                    return cache.get();
                }));
            }
            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
        }

        assertThat(upstream.calls()).isEqualTo(1);
        VehicleSnapshot shared = results.getFirst().get().snapshot();
        for (Future<SnapshotCache.CachedSnapshot> result : results) {
            assertThat(result.get().snapshot()).isSameAs(shared);
            assertThat(result.get().stale()).isFalse();
        }
    }

    @Test
    void servesStaleSnapshotWhileRateLimitIsAlmostUsedUp() {
        SnapshotCache.CachedSnapshot first = cache.get();
        recordRateLimit(2, Duration.ofMinutes(30));
        clock.advance(TTL);

        SnapshotCache.CachedSnapshot guarded = cache.get();

        assertThat(upstream.calls()).isEqualTo(1);
        assertThat(guarded.stale()).isTrue();
        assertThat(guarded.snapshot()).isSameAs(first.snapshot());
    }

    @Test
    void callsUpstreamAgainOnceRateLimitWindowHasReset() {
        cache.get();
        recordRateLimit(0, Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(10));

        SnapshotCache.CachedSnapshot refreshed = cache.get();

        assertThat(upstream.calls()).isEqualTo(2);
        assertThat(refreshed.stale()).isFalse();
    }

    @Test
    void callsUpstreamWhileEnoughRequestsRemain() {
        cache.get();
        recordRateLimit(3, Duration.ofMinutes(30));
        clock.advance(TTL);

        assertThat(cache.get().stale()).isFalse();
        assertThat(upstream.calls()).isEqualTo(2);
    }

    @Test
    void failsWithRetryAfterWhenRateLimitIsAlmostUsedUpAndNothingIsCached() {
        recordRateLimit(1, Duration.ofSeconds(300));
        clock.advance(Duration.ofMillis(500));

        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(cache::get)
                .satisfies(ex -> {
                    // 299.5 s rounded up
                    assertThat(ex.retryAfter()).contains(Duration.ofSeconds(300));
                    assertThat(ex.upstreamStatus()).isEmpty();
                    assertThat(ex.getMessage()).contains("Try again in 300 seconds");
                });
        assertThat(upstream.calls()).isZero();
    }

    @Test
    void honoursPendingRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(UpstreamHeaders.RETRY_AFTER, "120");
        apiKeyStatus.recordResponse(headers);

        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(cache::get)
                .satisfies(ex -> assertThat(ex.retryAfter()).contains(Duration.ofSeconds(120)));
        assertThat(upstream.calls()).isZero();

        clock.advance(Duration.ofSeconds(120));
        assertThat(cache.get().stale()).isFalse();
        assertThat(upstream.calls()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("transientErrors")
    void servesStaleSnapshotOnTransientUpstreamError(SkodaApiException error) {
        SnapshotCache.CachedSnapshot first = cache.get();
        clock.advance(TTL);
        upstream.failWith(error);

        SnapshotCache.CachedSnapshot fallback = cache.get();

        assertThat(fallback.stale()).isTrue();
        assertThat(fallback.snapshot()).isSameAs(first.snapshot());
        assertThat(upstream.calls()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("transientErrors")
    void rethrowsTransientUpstreamErrorWhenNothingIsCached(SkodaApiException error) {
        upstream.failWith(error);

        assertThatThrownBy(cache::get).isSameAs(error);
    }

    @ParameterizedTest
    @MethodSource("errorsThatMustNotBeMasked")
    void neverMasksPermanentErrorsWithStaleData(SkodaApiException error) {
        cache.get();
        clock.advance(TTL);
        upstream.failWith(error);

        assertThatThrownBy(cache::get).isSameAs(error);
    }

    @Test
    void returnsFreshDataAgainAfterUpstreamRecovers() {
        cache.get();
        clock.advance(TTL);
        upstream.failWith(UpstreamUnavailableException.forStatus(HttpStatus.BAD_GATEWAY, null, null));
        assertThat(cache.get().stale()).isTrue();

        upstream.respondWith(UpstreamFixtures.partial());
        SnapshotCache.CachedSnapshot recovered = cache.get();

        assertThat(recovered.stale()).isFalse();
        assertThat(recovered.snapshot().fetchedAt()).isEqualTo(START.plus(TTL));
        assertThat(recovered.snapshot().unavailable()).hasSize(4);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new SnapshotCache(upstream.client, new VehicleSnapshotMapper(), apiKeyStatus,
                clock, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<SkodaApiException> transientErrors() {
        return Stream.of(
                new RateLimitExceededException(HttpStatus.TOO_MANY_REQUESTS, null, Duration.ofSeconds(60)),
                UpstreamUnavailableException.forStatus(HttpStatus.SERVICE_UNAVAILABLE, null, null),
                UpstreamUnavailableException.forIoError(new SocketTimeoutException("Read timed out")));
    }

    static Stream<SkodaApiException> errorsThatMustNotBeMasked() {
        return Stream.of(
                new ApiKeyExpiredException(HttpStatus.UNAUTHORIZED, null),
                new ApiKeyInvalidException(HttpStatus.UNAUTHORIZED, null),
                new ApiKeyNotAuthorizedException(HttpStatus.FORBIDDEN, null),
                new VehicleNotFoundException(HttpStatus.NOT_FOUND, null),
                new UnexpectedUpstreamResponseException(HttpStatus.BAD_REQUEST, null));
    }

    private void recordRateLimit(int remaining, Duration resetIn) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(UpstreamHeaders.RATE_LIMIT_LIMIT, "20");
        headers.set(UpstreamHeaders.RATE_LIMIT_REMAINING, Integer.toString(remaining));
        headers.set(UpstreamHeaders.RATE_LIMIT_RESET, Long.toString(resetIn.toSeconds()));
        apiKeyStatus.recordResponse(headers);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
