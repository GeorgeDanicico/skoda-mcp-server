package com.gd.skoda.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.support.MutableClock;
import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.UpstreamHeaders;
import com.gd.skoda.upstream.UpstreamUnavailableException;
import com.gd.skoda.upstream.publicapi.model.FuelStatus;
import com.gd.skoda.upstream.publicapi.model.Vehicle;
import com.gd.skoda.upstream.publicapi.model.VehicleError;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class VehicleServiceTest {

    private static final Instant START = Instant.parse("2026-10-11T08:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(START);
    private final ApiKeyStatus apiKeyStatus = new ApiKeyStatus(clock);
    private final FakeUpstream upstream = new FakeUpstream();
    private final VehicleService service = new VehicleService(
            new SnapshotCache(upstream.client, new VehicleSnapshotMapper(), apiKeyStatus, clock, TTL),
            apiKeyStatus, clock);

    @Test
    void allViewsShareOneUpstreamCall() {
        VehicleData<VehicleOverview> overview = service.getOverview();
        VehicleData<Location> location = service.getLocation();
        VehicleData<Range> range = service.getRange();
        VehicleData<Status> status = service.getStatus();

        assertThat(upstream.calls()).isEqualTo(1);
        assertThat(location.data()).isEqualTo(overview.data().location());
        assertThat(range.data()).isEqualTo(overview.data().range());
        assertThat(status.data()).isEqualTo(overview.data().status());
        assertThat(List.of(overview.freshness(), location.freshness(), range.freshness(), status.freshness()))
                .containsOnly(new Freshness(START, false, List.of()));
    }

    @Test
    void listsOnlyRelevantUnavailablePartsPerView() {
        upstream.respondWith(UpstreamFixtures.partial());

        assertThat(service.getOverview().freshness().unavailable()).extracting(UnavailablePart::type)
                .containsExactly("RENDER_UNAVAILABLE", "PARKING_POSITION_UNAVAILABLE", "FUEL_STATUS_DISABLED",
                        "CHARGING_UNSUPPORTED");
        assertThat(service.getLocation().freshness().unavailable()).extracting(UnavailablePart::type)
                .containsExactly("PARKING_POSITION_UNAVAILABLE");
        // no range data at all, so CHARGING_UNSUPPORTED stays as part of the explanation
        assertThat(service.getRange().freshness().unavailable()).extracting(UnavailablePart::type)
                .containsExactly("FUEL_STATUS_DISABLED", "CHARGING_UNSUPPORTED");
        assertThat(service.getStatus().freshness().unavailable()).isEmpty();
        assertThat(service.getLocation().data()).isEqualTo(Location.EMPTY);
    }

    @Test
    void omitsUnsupportedChargingFromRangeOfCombustionCar() {
        upstream.respondWith(new VehicleResponse()
                .vehicle(new Vehicle().fuelStatus(new FuelStatus().carType("DIESEL")
                        .totalRangeInKm(new BigDecimal("800")).adBlueRange(new BigDecimal("4000"))))
                .errors(new ArrayList<>(List.of(
                        new VehicleError().type("CHARGING_UNSUPPORTED"),
                        new VehicleError().type("ODOMETER_UNAVAILABLE")))));

        VehicleData<Range> range = service.getRange();

        assertThat(range.data().adBlueRangeKm()).isEqualTo(4000);
        assertThat(range.freshness().unavailable()).isEmpty();
        assertThat(service.getStatus().freshness().unavailable()).extracting(UnavailablePart::part)
                .containsExactly(DataPart.ODOMETER);
    }

    @Test
    void marksStaleDataInEveryView() {
        service.getOverview();
        clock.advance(TTL.plusMinutes(1));
        upstream.failWith(UpstreamUnavailableException.forStatus(HttpStatus.INTERNAL_SERVER_ERROR, null, null));

        VehicleData<Location> location = service.getLocation();

        assertThat(location.freshness()).isEqualTo(new Freshness(START, true, List.of()));
        assertThat(location.data().state()).isEqualTo(ParkingState.PARKED);
    }

    @Test
    void reportsApiKeyInfoWithoutCallingUpstream() {
        assertThat(service.getApiKeyInfo()).isEqualTo(new ApiKeyInfo(null, null, null, null, null, null));

        HttpHeaders headers = new HttpHeaders();
        headers.set(UpstreamHeaders.API_KEY_EXPIRES_AT, "2026-10-18T07:00:00Z");
        headers.set(UpstreamHeaders.RATE_LIMIT_LIMIT, "20");
        headers.set(UpstreamHeaders.RATE_LIMIT_REMAINING, "17");
        headers.set(UpstreamHeaders.RATE_LIMIT_RESET, "1800");
        apiKeyStatus.recordResponse(headers);

        assertThat(service.getApiKeyInfo()).isEqualTo(new ApiKeyInfo(Instant.parse("2026-10-18T07:00:00Z"), 6L,
                20, 17, START.plusSeconds(1800), START));
        assertThat(upstream.calls()).isZero();
    }

    @Test
    void reportsNegativeDaysForExpiredKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(UpstreamHeaders.API_KEY_EXPIRES_AT, "2026-10-11T07:00:00Z");
        apiKeyStatus.recordResponse(headers);

        assertThat(service.getApiKeyInfo().daysUntilExpiry()).isEqualTo(-1L);
    }
}
