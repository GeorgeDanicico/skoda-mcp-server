package com.gd.skoda.vehicle;

import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.SkodaApiException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Read-only access to the configured vehicle. Every vehicle view is cut from the same cached
 * {@link VehicleSnapshot}, so asking for overview, location, range and status costs at most one
 * upstream call per cache TTL.
 *
 * <p>The vehicle reads throw {@link SkodaApiException} subclasses when no data can be served; see
 * {@link SnapshotCache} for when stale data is served instead.
 */
public final class VehicleService {

    private static final Set<DataPart> LOCATION_PARTS = EnumSet.of(DataPart.PARKING_POSITION);
    private static final Set<DataPart> RANGE_PARTS = EnumSet.of(DataPart.FUEL_STATUS, DataPart.CHARGING);
    private static final Set<DataPart> STATUS_PARTS = EnumSet.of(DataPart.VEHICLE_STATUS, DataPart.ODOMETER);

    private final SnapshotCache cache;
    private final ApiKeyStatus apiKeyStatus;
    private final Clock clock;

    public VehicleService(SnapshotCache cache, ApiKeyStatus apiKeyStatus, Clock clock) {
        this.cache = cache;
        this.apiKeyStatus = apiKeyStatus;
        this.clock = clock;
    }

    /** Everything known about the vehicle; {@code unavailable} lists every upstream error. */
    public VehicleData<VehicleOverview> getOverview() {
        return view(Function.identity(), (snapshot, part) -> true);
    }

    /** Last known position; {@code unavailable} lists parking position errors. */
    public VehicleData<Location> getLocation() {
        return view(VehicleOverview::location, (snapshot, part) -> LOCATION_PARTS.contains(part.part()));
    }

    /**
     * Range and energy levels; {@code unavailable} lists fuel status and charging errors. When range data
     * was delivered, {@code *_UNSUPPORTED} errors are left out: a combustion car always reports
     * {@code CHARGING_UNSUPPORTED} and an EV {@code FUEL_STATUS_UNSUPPORTED}, which is not missing data.
     */
    public VehicleData<Range> getRange() {
        return view(VehicleOverview::range, VehicleService::isRelevantForRange);
    }

    /** Lock/door/window/light state and odometer; {@code unavailable} lists vehicle status and odometer errors. */
    public VehicleData<Status> getStatus() {
        return view(VehicleOverview::status, (snapshot, part) -> STATUS_PARTS.contains(part.part()));
    }

    /** API key and quota state as of the last upstream response. Never calls upstream. */
    public ApiKeyInfo getApiKeyInfo() {
        return ApiKeyInfo.from(apiKeyStatus.current(), clock.instant());
    }

    private <T> VehicleData<T> view(Function<VehicleOverview, T> extractor,
            BiPredicate<VehicleSnapshot, UnavailablePart> relevant) {
        SnapshotCache.CachedSnapshot cached = cache.get();
        VehicleSnapshot snapshot = cached.snapshot();
        List<UnavailablePart> unavailable = snapshot.unavailable().stream()
                .filter(part -> relevant.test(snapshot, part))
                .toList();
        return new VehicleData<>(extractor.apply(snapshot.vehicle()),
                new Freshness(snapshot.fetchedAt(), cached.stale(), unavailable));
    }

    private static boolean isRelevantForRange(VehicleSnapshot snapshot, UnavailablePart part) {
        if (!RANGE_PARTS.contains(part.part())) {
            return false;
        }
        boolean rangeDelivered = !Range.EMPTY.equals(snapshot.vehicle().range());
        return !(rangeDelivered && part.reason() == UnavailabilityReason.UNSUPPORTED);
    }
}
