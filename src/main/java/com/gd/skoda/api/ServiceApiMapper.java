package com.gd.skoda.api;

import com.gd.skoda.api.generated.model.ApiKeyInfoDto;
import com.gd.skoda.api.generated.model.CarTypeDto;
import com.gd.skoda.api.generated.model.DataPartDto;
import com.gd.skoda.api.generated.model.DoorsLockedStateDto;
import com.gd.skoda.api.generated.model.EngineRangeDto;
import com.gd.skoda.api.generated.model.EngineTypeDto;
import com.gd.skoda.api.generated.model.FreshnessDto;
import com.gd.skoda.api.generated.model.LightsStateDto;
import com.gd.skoda.api.generated.model.LocationDto;
import com.gd.skoda.api.generated.model.LockStateDto;
import com.gd.skoda.api.generated.model.OpenStateDto;
import com.gd.skoda.api.generated.model.ParkingStateDto;
import com.gd.skoda.api.generated.model.RangeDto;
import com.gd.skoda.api.generated.model.ReliableLockStateDto;
import com.gd.skoda.api.generated.model.StatusDto;
import com.gd.skoda.api.generated.model.UnavailabilityReasonDto;
import com.gd.skoda.api.generated.model.UnavailablePartDto;
import com.gd.skoda.api.generated.model.VehicleLocationResponseDto;
import com.gd.skoda.api.generated.model.VehicleOverviewDto;
import com.gd.skoda.api.generated.model.VehicleOverviewResponseDto;
import com.gd.skoda.api.generated.model.VehicleRangeResponseDto;
import com.gd.skoda.api.generated.model.VehicleStatusResponseDto;
import com.gd.skoda.vehicle.ApiKeyInfo;
import com.gd.skoda.vehicle.EngineRange;
import com.gd.skoda.vehicle.Freshness;
import com.gd.skoda.vehicle.Location;
import com.gd.skoda.vehicle.Range;
import com.gd.skoda.vehicle.Status;
import com.gd.skoda.vehicle.UnavailablePart;
import com.gd.skoda.vehicle.VehicleData;
import com.gd.skoda.vehicle.VehicleOverview;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/**
 * Maps the {@code com.gd.skoda.vehicle} domain records to the DTOs generated from
 * {@code skoda-service-api.yaml}. Stateless and side-effect free.
 *
 * <ul>
 *   <li>{@code null} stays {@code null} (unknown); nothing is defaulted.</li>
 *   <li>Enums are mapped by constant name: the contract's enums mirror the domain enums one to one
 *       (asserted by {@code ServiceApiMapperTest}).</li>
 *   <li>{@link Instant}s become UTC {@link OffsetDateTime}s.</li>
 * </ul>
 */
public final class ServiceApiMapper {

    public VehicleOverviewResponseDto toOverviewResponse(VehicleData<VehicleOverview> data) {
        return new VehicleOverviewResponseDto(toDto(data.data()), toDto(data.freshness()));
    }

    public VehicleLocationResponseDto toLocationResponse(VehicleData<Location> data) {
        return new VehicleLocationResponseDto(toDto(data.data()), toDto(data.freshness()));
    }

    public VehicleRangeResponseDto toRangeResponse(VehicleData<Range> data) {
        return new VehicleRangeResponseDto(toDto(data.data()), toDto(data.freshness()));
    }

    public VehicleStatusResponseDto toStatusResponse(VehicleData<Status> data) {
        return new VehicleStatusResponseDto(toDto(data.data()), toDto(data.freshness()));
    }

    public ApiKeyInfoDto toDto(ApiKeyInfo info) {
        return new ApiKeyInfoDto()
                .keyExpiresAt(utc(info.keyExpiresAt()))
                .daysUntilExpiry(info.daysUntilExpiry())
                .rateLimitLimit(info.rateLimitLimit())
                .rateLimitRemaining(info.rateLimitRemaining())
                .rateLimitResetAt(utc(info.rateLimitResetAt()))
                .lastUpstreamCallAt(utc(info.lastUpstreamCallAt()));
    }

    VehicleOverviewDto toDto(VehicleOverview overview) {
        return new VehicleOverviewDto(toDto(overview.location()), toDto(overview.range()), toDto(overview.status()))
                .name(overview.name())
                .licensePlate(overview.licensePlate())
                .capturedAt(utc(overview.capturedAt()));
    }

    LocationDto toDto(Location location) {
        return new LocationDto()
                .state(map(location.state(), ParkingStateDto.class))
                .latitude(location.latitude())
                .longitude(location.longitude())
                .formattedAddress(location.formattedAddress())
                .capturedAt(utc(location.capturedAt()));
    }

    RangeDto toDto(Range range) {
        return new RangeDto()
                .carType(map(range.carType(), CarTypeDto.class))
                .totalRangeKm(range.totalRangeKm())
                .primaryEngine(toDto(range.primaryEngine()))
                .secondaryEngine(toDto(range.secondaryEngine()))
                .adBlueRangeKm(range.adBlueRangeKm())
                .batterySocPercent(range.batterySocPercent())
                .capturedAt(utc(range.capturedAt()));
    }

    @Nullable EngineRangeDto toDto(@Nullable EngineRange engine) {
        if (engine == null) {
            return null;
        }
        return new EngineRangeDto()
                .engineType(map(engine.engineType(), EngineTypeDto.class))
                .fuelLevelPercent(engine.fuelLevelPercent())
                .socPercent(engine.socPercent())
                .remainingRangeKm(engine.remainingRangeKm());
    }

    StatusDto toDto(Status status) {
        return new StatusDto()
                .locked(map(status.locked(), LockStateDto.class))
                .doorsLocked(map(status.doorsLocked(), DoorsLockedStateDto.class))
                .doors(map(status.doors(), OpenStateDto.class))
                .windows(map(status.windows(), OpenStateDto.class))
                .lights(map(status.lights(), LightsStateDto.class))
                .reliableLockStatus(map(status.reliableLockStatus(), ReliableLockStateDto.class))
                .trunk(map(status.trunk(), OpenStateDto.class))
                .bonnet(map(status.bonnet(), OpenStateDto.class))
                .sunroof(map(status.sunroof(), OpenStateDto.class))
                .odometerKm(status.odometerKm())
                .capturedAt(utc(status.capturedAt()));
    }

    FreshnessDto toDto(Freshness freshness) {
        return new FreshnessDto(utc(freshness.fetchedAt()), freshness.stale(),
                freshness.unavailable().stream().map(this::toDto).toList());
    }

    UnavailablePartDto toDto(UnavailablePart part) {
        return new UnavailablePartDto(map(part.part(), DataPartDto.class),
                map(part.reason(), UnavailabilityReasonDto.class))
                .type(part.type())
                .description(part.description());
    }

    /** Maps a domain enum to the contract enum with the same constant name; {@code null} stays {@code null}. */
    static <T extends Enum<T>> @Nullable T map(@Nullable Enum<?> source, Class<T> target) {
        return source == null ? null : Enum.valueOf(target, source.name());
    }

    private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
