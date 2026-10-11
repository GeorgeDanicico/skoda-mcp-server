package com.gd.skoda.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.api.generated.model.ApiKeyInfoDto;
import com.gd.skoda.api.generated.model.CarTypeDto;
import com.gd.skoda.api.generated.model.DataPartDto;
import com.gd.skoda.api.generated.model.DoorsLockedStateDto;
import com.gd.skoda.api.generated.model.EngineTypeDto;
import com.gd.skoda.api.generated.model.LightsStateDto;
import com.gd.skoda.api.generated.model.LockStateDto;
import com.gd.skoda.api.generated.model.OpenStateDto;
import com.gd.skoda.api.generated.model.ParkingStateDto;
import com.gd.skoda.api.generated.model.ReliableLockStateDto;
import com.gd.skoda.api.generated.model.UnavailabilityReasonDto;
import com.gd.skoda.api.generated.model.VehicleLocationResponseDto;
import com.gd.skoda.api.generated.model.VehicleOverviewResponseDto;
import com.gd.skoda.api.generated.model.VehicleRangeResponseDto;
import com.gd.skoda.api.generated.model.VehicleStatusResponseDto;
import com.gd.skoda.vehicle.ApiKeyInfo;
import com.gd.skoda.vehicle.CarType;
import com.gd.skoda.vehicle.DataPart;
import com.gd.skoda.vehicle.DoorsLockedState;
import com.gd.skoda.vehicle.EngineType;
import com.gd.skoda.vehicle.LightsState;
import com.gd.skoda.vehicle.Location;
import com.gd.skoda.vehicle.LockState;
import com.gd.skoda.vehicle.OpenState;
import com.gd.skoda.vehicle.ParkingState;
import com.gd.skoda.vehicle.Range;
import com.gd.skoda.vehicle.ReliableLockState;
import com.gd.skoda.vehicle.Status;
import com.gd.skoda.vehicle.UnavailabilityReason;
import com.gd.skoda.vehicle.VehicleData;
import com.gd.skoda.vehicle.VehicleOverview;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceApiMapperTest {

    private final ServiceApiMapper mapper = new ServiceApiMapper();

    @Test
    void mapsOverviewWithAllFields() {
        VehicleOverviewResponseDto dto = mapper.toOverviewResponse(ApiTestData.fullOverview());

        assertThat(dto.getData().getName()).isEqualTo("Octavia");
        assertThat(dto.getData().getLicensePlate()).isEqualTo("1AB 2345");
        assertThat(dto.getData().getCapturedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 9, 30, 0, 0, ZoneOffset.UTC));

        var location = dto.getData().getLocation();
        assertThat(location.getState()).isEqualTo(ParkingStateDto.PARKED);
        assertThat(location.getLatitude()).isEqualTo(50.4113);
        assertThat(location.getLongitude()).isEqualTo(14.9083);
        assertThat(location.getFormattedAddress()).isEqualTo("tř. Václava Klementa 869, 293 01 Mladá Boleslav, CZ");
        assertThat(location.getCapturedAt()).isNull();

        var range = dto.getData().getRange();
        assertThat(range.getCarType()).isEqualTo(CarTypeDto.DIESEL);
        assertThat(range.getTotalRangeKm()).isEqualTo(640);
        assertThat(range.getPrimaryEngine().getEngineType()).isEqualTo(EngineTypeDto.DIESEL);
        assertThat(range.getPrimaryEngine().getFuelLevelPercent()).isEqualTo(55);
        assertThat(range.getPrimaryEngine().getSocPercent()).isNull();
        assertThat(range.getPrimaryEngine().getRemainingRangeKm()).isEqualTo(640);
        assertThat(range.getSecondaryEngine()).isNull();
        assertThat(range.getAdBlueRangeKm()).isEqualTo(4000);
        assertThat(range.getBatterySocPercent()).isNull();
        assertThat(range.getCapturedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 9, 30, 0, 0, ZoneOffset.UTC));

        var status = dto.getData().getStatus();
        assertThat(status.getLocked()).isEqualTo(LockStateDto.YES);
        assertThat(status.getDoorsLocked()).isEqualTo(DoorsLockedStateDto.YES);
        assertThat(status.getDoors()).isEqualTo(OpenStateDto.CLOSED);
        assertThat(status.getWindows()).isEqualTo(OpenStateDto.CLOSED);
        assertThat(status.getLights()).isEqualTo(LightsStateDto.OFF);
        assertThat(status.getReliableLockStatus()).isEqualTo(ReliableLockStateDto.LOCKED);
        assertThat(status.getTrunk()).isEqualTo(OpenStateDto.CLOSED);
        assertThat(status.getBonnet()).isEqualTo(OpenStateDto.CLOSED);
        assertThat(status.getSunroof()).isEqualTo(OpenStateDto.UNSUPPORTED);
        assertThat(status.getOdometerKm()).isEqualTo(48_210L);
        assertThat(status.getCapturedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 9, 29, 0, 0, ZoneOffset.UTC));

        var freshness = dto.getFreshness();
        assertThat(freshness.getFetchedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(freshness.getStale()).isFalse();
        assertThat(freshness.getUnavailable()).singleElement().satisfies(part -> {
            assertThat(part.getPart()).isEqualTo(DataPartDto.CHARGING);
            assertThat(part.getReason()).isEqualTo(UnavailabilityReasonDto.UNSUPPORTED);
            assertThat(part.getType()).isEqualTo("CHARGING_UNSUPPORTED");
            assertThat(part.getDescription()).isEqualTo("Charging is not supported");
        });
    }

    @Test
    void keepsUnknownValuesNull() {
        VehicleOverviewResponseDto dto = mapper.toOverviewResponse(ApiTestData.emptyOverview(true));

        assertThat(dto.getData().getName()).isNull();
        assertThat(dto.getData().getCapturedAt()).isNull();
        assertThat(dto.getData().getLocation().getState()).isNull();
        assertThat(dto.getData().getLocation().getLatitude()).isNull();
        assertThat(dto.getData().getRange().getPrimaryEngine()).isNull();
        assertThat(dto.getData().getRange().getTotalRangeKm()).isNull();
        assertThat(dto.getData().getStatus().getLocked()).isNull();
        assertThat(dto.getData().getStatus().getOdometerKm()).isNull();
        assertThat(dto.getFreshness().getStale()).isTrue();
        assertThat(dto.getFreshness().getUnavailable()).isEmpty();
    }

    @Test
    void mapsEachViewWithItsFreshness() {
        VehicleData<VehicleOverview> overview = ApiTestData.fullOverview();
        var freshness = overview.freshness();

        VehicleLocationResponseDto location = mapper.toLocationResponse(
                new VehicleData<>(overview.data().location(), freshness));
        VehicleRangeResponseDto range = mapper.toRangeResponse(new VehicleData<>(overview.data().range(), freshness));
        VehicleStatusResponseDto status = mapper.toStatusResponse(new VehicleData<>(overview.data().status(), freshness));

        assertThat(location.getData().getState()).isEqualTo(ParkingStateDto.PARKED);
        assertThat(range.getData().getTotalRangeKm()).isEqualTo(640);
        assertThat(status.getData().getOdometerKm()).isEqualTo(48_210L);
        assertThat(List.of(location.getFreshness(), range.getFreshness(), status.getFreshness()))
                .allSatisfy(f -> assertThat(f.getFetchedAt().toInstant()).isEqualTo(freshness.fetchedAt()));
    }

    @Test
    void mapsInMotionWithoutPosition() {
        var dto = mapper.toLocationResponse(new VehicleData<>(
                new Location(ParkingState.IN_MOTION, null, null, null, null), ApiTestData.freshness(false)));

        assertThat(dto.getData().getState()).isEqualTo(ParkingStateDto.IN_MOTION);
        assertThat(dto.getData().getLatitude()).isNull();
        assertThat(dto.getData().getFormattedAddress()).isNull();
    }

    @Test
    void mapsApiKeyInfo() {
        ApiKeyInfoDto dto = mapper.toDto(ApiTestData.apiKeyInfo());

        assertThat(dto.getKeyExpiresAt()).isEqualTo(OffsetDateTime.of(2026, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getDaysUntilExpiry()).isEqualTo(80L);
        assertThat(dto.getRateLimitLimit()).isEqualTo(20);
        assertThat(dto.getRateLimitRemaining()).isEqualTo(17);
        assertThat(dto.getRateLimitResetAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 10, 45, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getLastUpstreamCallAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void mapsEmptyApiKeyInfoToNulls() {
        ApiKeyInfoDto dto = mapper.toDto(new ApiKeyInfo(null, null, null, null, null, null));

        assertThat(dto).isEqualTo(new ApiKeyInfoDto());
    }

    /** The contract's enums must mirror the domain enums, or {@link ServiceApiMapper#map} would throw. */
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("enumPairs")
    <D extends Enum<D>, T extends Enum<T>> void contractEnumsMirrorDomainEnums(Class<D> domain, Class<T> contract) {
        assertThat(names(contract)).containsExactlyElementsOf(names(domain));
        for (D constant : domain.getEnumConstants()) {
            assertThat(ServiceApiMapper.map(constant, contract)).extracting(Enum::name).isEqualTo(constant.name());
        }
        assertThat(ServiceApiMapper.map(null, contract)).isNull();
    }

    static Stream<Arguments> enumPairs() {
        return Stream.of(
                Arguments.of(ParkingState.class, ParkingStateDto.class),
                Arguments.of(LockState.class, LockStateDto.class),
                Arguments.of(DoorsLockedState.class, DoorsLockedStateDto.class),
                Arguments.of(OpenState.class, OpenStateDto.class),
                Arguments.of(LightsState.class, LightsStateDto.class),
                Arguments.of(ReliableLockState.class, ReliableLockStateDto.class),
                Arguments.of(CarType.class, CarTypeDto.class),
                Arguments.of(EngineType.class, EngineTypeDto.class),
                Arguments.of(DataPart.class, DataPartDto.class),
                Arguments.of(UnavailabilityReason.class, UnavailabilityReasonDto.class));
    }

    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    @Test
    void emptyRecordsMapToEmptyDtos() {
        assertThat(mapper.toDto(Range.EMPTY).getCarType()).isNull();
        assertThat(mapper.toDto(Status.EMPTY).getSunroof()).isNull();
        assertThat(mapper.toDto(Location.EMPTY).getLongitude()).isNull();
    }
}
