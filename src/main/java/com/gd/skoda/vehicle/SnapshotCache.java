package com.gd.skoda.vehicle;

import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.PublicApiClient;
import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.upstream.SkodaApiException;
import com.gd.skoda.upstream.UpstreamUnavailableException;
import com.gd.skoda.upstream.publicapi.model.VehiclePart;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the latest {@link VehicleSnapshot} and refreshes it with one upstream call when it is older
 * than the TTL.
 *
 * <ul>
 *   <li><b>Single flight</b>: refreshes run under a {@link ReentrantLock} (virtual-thread friendly);
 *       callers that queue up behind a refresh re-check the cache and reuse its result.</li>
 *   <li><b>Rate-limit guard</b>: no upstream call while {@code RateLimit-Remaining} is at most
 *       {@value #RATE_LIMIT_RESERVE} before the window reset, or while a {@code Retry-After} is pending;
 *       the cached snapshot is served as stale instead.</li>
 *   <li><b>Stale fallback</b>: an upstream 429, 5xx, I/O error or timeout serves the cached snapshot as
 *       stale. Every other error (API key expired/invalid/not authorised, vehicle not found, unexpected
 *       response) is rethrown, because old data would hide a problem the user has to fix.</li>
 * </ul>
 */
public final class SnapshotCache {

    /** Requests kept in reserve: at or below this many remaining requests, the cache stops calling upstream. */
    static final int RATE_LIMIT_RESERVE = 2;

    /** The parts every refresh asks for; {@code charging} is cheap and makes range work for PHEV/EV. */
    static final Set<VehiclePart> PARTS = Collections.unmodifiableSet(EnumSet.of(VehiclePart.INFO,
            VehiclePart.STATUS, VehiclePart.FUEL_STATUS, VehiclePart.ODOMETER, VehiclePart.PARKING_POSITION,
            VehiclePart.CHARGING));

    private static final Logger log = LoggerFactory.getLogger(SnapshotCache.class);

    private final PublicApiClient client;
    private final VehicleSnapshotMapper mapper;
    private final ApiKeyStatus apiKeyStatus;
    private final Clock clock;
    private final Duration ttl;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile @Nullable VehicleSnapshot snapshot;

    public SnapshotCache(PublicApiClient client, VehicleSnapshotMapper mapper, ApiKeyStatus apiKeyStatus,
            Clock clock, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive: " + ttl);
        }
        this.client = client;
        this.mapper = mapper;
        this.apiKeyStatus = apiKeyStatus;
        this.clock = clock;
        this.ttl = ttl;
    }

    /**
     * Returns the cached snapshot if it is younger than the TTL, otherwise refreshes it (or falls back
     * to the cached one, marked stale).
     *
     * @throws SkodaApiException if there is no usable snapshot, or for errors that must not be masked
     */
    public CachedSnapshot get() {
        VehicleSnapshot current = snapshot;
        if (isFresh(current)) {
            return new CachedSnapshot(current, false);
        }
        refreshLock.lock();
        try {
            // another caller may have refreshed while this one waited for the lock
            current = snapshot;
            if (isFresh(current)) {
                return new CachedSnapshot(current, false);
            }
            return refresh(current);
        } finally {
            refreshLock.unlock();
        }
    }

    private CachedSnapshot refresh(@Nullable VehicleSnapshot previous) {
        Instant now = clock.instant();
        Instant blockedUntil = rateLimitBlockedUntil(apiKeyStatus.current(), now);
        if (blockedUntil != null) {
            if (previous != null) {
                log.info("Rate limit almost used up; serving cached vehicle data from {} until {}",
                        previous.fetchedAt(), blockedUntil);
                return new CachedSnapshot(previous, true);
            }
            throw RateLimitExceededException.quotaExhausted(secondsUntil(now, blockedUntil));
        }

        VehicleResponse response;
        try {
            log.debug("Refreshing vehicle snapshot from the MySkoda Public API");
            response = client.fetchVehicle(PARTS);
        } catch (RateLimitExceededException | UpstreamUnavailableException ex) {
            if (previous == null) {
                throw ex;
            }
            log.warn("Vehicle refresh failed, serving cached vehicle data from {}: {}",
                    previous.fetchedAt(), ex.getMessage());
            return new CachedSnapshot(previous, true);
        }

        VehicleSnapshot refreshed = mapper.toSnapshot(response, clock.instant());
        snapshot = refreshed;
        log.info("Vehicle snapshot refreshed ({} unavailable part(s), rate limit remaining: {})",
                refreshed.unavailable().size(), apiKeyStatus.current().rateLimitRemaining());
        return new CachedSnapshot(refreshed, false);
    }

    private boolean isFresh(@Nullable VehicleSnapshot candidate) {
        return candidate != null && clock.instant().isBefore(candidate.fetchedAt().plus(ttl));
    }

    /** When upstream may be called again, or {@code null} if it may be called now. */
    static @Nullable Instant rateLimitBlockedUntil(ApiKeyStatus.Snapshot status, Instant now) {
        Instant blockedUntil = null;
        Integer remaining = status.rateLimitRemaining();
        Instant resetAt = status.rateLimitResetAt();
        if (remaining != null && remaining <= RATE_LIMIT_RESERVE && resetAt != null && resetAt.isAfter(now)) {
            blockedUntil = resetAt;
        }
        Instant retryNotBefore = status.retryNotBefore();
        if (retryNotBefore != null && retryNotBefore.isAfter(now)
                && (blockedUntil == null || retryNotBefore.isAfter(blockedUntil))) {
            blockedUntil = retryNotBefore;
        }
        return blockedUntil;
    }

    /** Rounded up to whole seconds, so clients don't retry a fraction of a second too early. */
    private static Duration secondsUntil(Instant now, Instant later) {
        Duration wait = Duration.between(now, later);
        return wait.getNano() == 0 ? wait : Duration.ofSeconds(wait.getSeconds() + 1);
    }

    /**
     * A snapshot served by the cache.
     *
     * @param snapshot the vehicle data
     * @param stale    {@code true} if a refresh was due but not possible
     */
    public record CachedSnapshot(VehicleSnapshot snapshot, boolean stale) {
    }
}
