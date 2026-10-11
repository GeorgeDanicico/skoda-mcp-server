package com.gd.skoda.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.upstream.publicapi.model.BatteryStatus;
import com.gd.skoda.upstream.publicapi.model.Charging;
import com.gd.skoda.upstream.publicapi.model.ChargingStatus;
import com.gd.skoda.upstream.publicapi.model.FuelStatus;
import com.gd.skoda.upstream.publicapi.model.OverallVehicleStatus;
import com.gd.skoda.upstream.publicapi.model.ParkingPosition;
import com.gd.skoda.upstream.publicapi.model.Vehicle;
import com.gd.skoda.upstream.publicapi.model.VehicleError;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import com.gd.skoda.upstream.publicapi.model.VehicleStatus;
import com.gd.skoda.upstream.publicapi.model.VehicleStatusDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleSnapshotMapperTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-11T08:00:00Z");
    private static final Instant CAPTURED_AT = Instant.parse("2026-10-11T07:58:12Z");

    private final VehicleSnapshotMapper mapper = new VehicleSnapshotMapper();

    @Test
    void mapsFullFixture() {
        VehicleSnapshot snapshot = mapper.toSnapshot(UpstreamFixtures.full(), FETCHED_AT);

        assertThat(snapshot.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(snapshot.unavailable()).isEmpty();
        VehicleOverview vehicle = snapshot.vehicle();
        assertThat(vehicle.name()).isEqualTo("Family Octavia");
        assertThat(vehicle.licensePlate()).isEqualTo("B 123 XYZ");
        assertThat(vehicle.capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(vehicle.location()).isEqualTo(new Location(ParkingState.PARKED, 44.4361414, 26.1027202,
                "Strada Lipscani 12, 030031 Bucharest, Romania", null));
        assertThat(vehicle.range()).isEqualTo(new Range(CarType.GASOLINE, 612,
                new EngineRange(EngineType.GASOLINE, 74, null, 612), null, null, null, CAPTURED_AT));
        assertThat(vehicle.status()).isEqualTo(new Status(LockState.YES, DoorsLockedState.YES, OpenState.CLOSED,
                OpenState.CLOSED, LightsState.OFF, ReliableLockState.LOCKED, OpenState.CLOSED, OpenState.CLOSED,
                OpenState.UNSUPPORTED, 48213L, CAPTURED_AT));
    }

    @Test
    void mapsPartialFixture() {
        VehicleSnapshot snapshot = mapper.toSnapshot(UpstreamFixtures.partial(), FETCHED_AT);

        VehicleOverview vehicle = snapshot.vehicle();
        assertThat(vehicle.name()).isEqualTo("Family Octavia");
        assertThat(vehicle.location()).isEqualTo(Location.EMPTY);
        assertThat(vehicle.range()).isEqualTo(Range.EMPTY);
        // +02:00 in the fixture
        Instant statusCapturedAt = Instant.parse("2026-10-11T05:58:12Z");
        assertThat(vehicle.status()).isEqualTo(new Status(LockState.NO, DoorsLockedState.NO, OpenState.CLOSED,
                OpenState.OPEN, LightsState.OFF, null, OpenState.CLOSED, OpenState.CLOSED, OpenState.UNSUPPORTED,
                48213L, statusCapturedAt));
        assertThat(vehicle.capturedAt()).isEqualTo(statusCapturedAt);
        assertThat(snapshot.unavailable()).containsExactly(
                new UnavailablePart(DataPart.RENDER, UnavailabilityReason.UNAVAILABLE, "RENDER_UNAVAILABLE",
                        "The vehicle render could not be retrieved."),
                new UnavailablePart(DataPart.PARKING_POSITION, UnavailabilityReason.UNAVAILABLE,
                        "PARKING_POSITION_UNAVAILABLE", "Parking position could not be retrieved from the vehicle."),
                new UnavailablePart(DataPart.FUEL_STATUS, UnavailabilityReason.DISABLED, "FUEL_STATUS_DISABLED", null),
                new UnavailablePart(DataPart.CHARGING, UnavailabilityReason.UNSUPPORTED, "CHARGING_UNSUPPORTED",
                        "Charging is not supported by the vehicle."));
    }

    @Test
    void mapsUnrecognisedValuesToUnknownAndAbsentValuesToNull() {
        VehicleResponse response = new VehicleResponse().vehicle(new Vehicle()
                .status(new VehicleStatus()
                        .overall(new OverallVehicleStatus().locked("MAYBE").doorsLocked("unknown").doors("ajar")
                                .windows("UNSUPPORTED").lights("on"))
                        .detail(new VehicleStatusDetail().trunk(" open ").bonnet("").sunroof("TILTED")))
                .fuelStatus(new FuelStatus().carType("HYDROGEN")
                        .primaryEngineRange(new com.gd.skoda.upstream.publicapi.model.EngineRange()
                                .engineType("FUEL_CELL")))
                .parkingPosition(new ParkingPosition().state("TELEPORTING")));

        VehicleOverview vehicle = mapper.toSnapshot(response, FETCHED_AT).vehicle();

        assertThat(vehicle.status()).isEqualTo(new Status(LockState.UNKNOWN, DoorsLockedState.UNKNOWN,
                OpenState.UNKNOWN, OpenState.UNSUPPORTED, LightsState.ON, null, OpenState.OPEN, null,
                OpenState.UNKNOWN, null, null));
        assertThat(vehicle.range().carType()).isEqualTo(CarType.UNKNOWN);
        assertThat(vehicle.range().primaryEngine()).isEqualTo(new EngineRange(EngineType.UNKNOWN, null, null, null));
        assertThat(vehicle.location()).isEqualTo(new Location(ParkingState.UNKNOWN, null, null, null, null));
        assertThat(vehicle.capturedAt()).isNull();
    }

    @Test
    void mapsUnrecognisedErrorTypesToUnknownPart() {
        VehicleResponse response = new VehicleResponse().vehicle(new Vehicle()).errors(new ArrayList<>(List.of(
                new VehicleError().type("WARP_DRIVE_UNAVAILABLE").description("new part"),
                new VehicleError().type("ODOMETER_EXPLODED"),
                new VehicleError().type("GARBAGE"),
                new VehicleError(),
                new VehicleError().type("odometer_disabled"))));

        assertThat(mapper.toSnapshot(response, FETCHED_AT).unavailable()).containsExactly(
                new UnavailablePart(DataPart.UNKNOWN, UnavailabilityReason.UNKNOWN, "WARP_DRIVE_UNAVAILABLE",
                        "new part"),
                new UnavailablePart(DataPart.UNKNOWN, UnavailabilityReason.UNKNOWN, "ODOMETER_EXPLODED", null),
                new UnavailablePart(DataPart.UNKNOWN, UnavailabilityReason.UNKNOWN, "GARBAGE", null),
                new UnavailablePart(DataPart.UNKNOWN, UnavailabilityReason.UNKNOWN, null, null),
                new UnavailablePart(DataPart.ODOMETER, UnavailabilityReason.DISABLED, "odometer_disabled", null));
    }

    @Test
    void mapsEmptyVehicleToEmptyRecords() {
        VehicleSnapshot snapshot = mapper.toSnapshot(new VehicleResponse().vehicle(new Vehicle()).errors(null),
                FETCHED_AT);

        assertThat(snapshot.vehicle()).isEqualTo(
                new VehicleOverview(null, null, Location.EMPTY, Range.EMPTY, Status.EMPTY, null));
        assertThat(snapshot.unavailable()).isEmpty();
    }

    @Test
    void mapsVehicleInMotionWithoutCoordinates() {
        VehicleResponse response = new VehicleResponse()
                .vehicle(new Vehicle().parkingPosition(new ParkingPosition().state("IN_MOTION")));

        assertThat(mapper.toSnapshot(response, FETCHED_AT).vehicle().location())
                .isEqualTo(new Location(ParkingState.IN_MOTION, null, null, null, null));
    }

    @Test
    void mapsOdometerWithoutStatus() {
        Instant odometerAt = Instant.parse("2026-10-11T07:00:00Z");
        VehicleResponse response = new VehicleResponse().vehicle(new Vehicle().odometer(
                new com.gd.skoda.upstream.publicapi.model.Odometer().mileageInKm(1234L)
                        .carCapturedTimestamp(OffsetDateTime.parse("2026-10-11T07:00:00Z"))));

        assertThat(mapper.toSnapshot(response, FETCHED_AT).vehicle().status()).isEqualTo(
                new Status(null, null, null, null, null, null, null, null, null, 1234L, odometerAt));
    }

    @Test
    void derivesRangeFromBatteryForElectricVehicle() {
        OffsetDateTime chargingAt = OffsetDateTime.parse("2026-10-11T07:30:00Z");
        VehicleResponse response = new VehicleResponse().vehicle(new Vehicle().charging(new Charging()
                .isVehicleInSavedLocation(false)
                .status(new ChargingStatus().battery(new BatteryStatus()
                        .stateOfChargeInPercent(71).remainingCruisingRangeInMeters(249_600)))
                .carCapturedTimestamp(chargingAt)));

        Range range = mapper.toSnapshot(response, FETCHED_AT).vehicle().range();

        assertThat(range).isEqualTo(new Range(null, 250, new EngineRange(EngineType.ELECTRIC, null, 71, 250), null,
                null, 71, chargingAt.toInstant()));
    }

    @Test
    void addsBatterySocToFuelRangeOfPluginHybrid() {
        VehicleResponse response = new VehicleResponse().vehicle(new Vehicle()
                .fuelStatus(new FuelStatus().carType("HYBRID").totalRangeInKm(new BigDecimal("700.5"))
                        .primaryEngineRange(new com.gd.skoda.upstream.publicapi.model.EngineRange()
                                .engineType("ELECTRIC").currentSoCInPercent(new BigDecimal("55"))
                                .remainingRangeInKm(new BigDecimal("40.4")))
                        .secondaryEngineRange(new com.gd.skoda.upstream.publicapi.model.EngineRange()
                                .engineType("GASOLINE").currentFuelLevelInPercent(new BigDecimal("80"))
                                .remainingRangeInKm(new BigDecimal("660"))))
                .charging(new Charging().isVehicleInSavedLocation(true)
                        .status(new ChargingStatus().battery(new BatteryStatus().stateOfChargeInPercent(56)))));

        Range range = mapper.toSnapshot(response, FETCHED_AT).vehicle().range();

        assertThat(range.carType()).isEqualTo(CarType.HYBRID);
        assertThat(range.totalRangeKm()).isEqualTo(701);
        assertThat(range.primaryEngine()).isEqualTo(new EngineRange(EngineType.ELECTRIC, null, 55, 40));
        assertThat(range.secondaryEngine()).isEqualTo(new EngineRange(EngineType.GASOLINE, 80, null, 660));
        // the battery status from charging wins over the engine range
        assertThat(range.batterySocPercent()).isEqualTo(56);
    }

    @Test
    void takesBatterySocFromElectricEngineRangeWithoutCharging() {
        VehicleResponse response = new VehicleResponse().vehicle(new Vehicle()
                .fuelStatus(new FuelStatus().carType("HYBRID")
                        .secondaryEngineRange(new com.gd.skoda.upstream.publicapi.model.EngineRange()
                                .engineType("ELECTRIC").currentSoCInPercent(new BigDecimal("42")))));

        assertThat(mapper.toSnapshot(response, FETCHED_AT).vehicle().range().batterySocPercent()).isEqualTo(42);
    }
}
