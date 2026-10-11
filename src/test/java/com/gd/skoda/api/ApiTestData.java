package com.gd.skoda.api;

import com.gd.skoda.vehicle.ApiKeyInfo;
import com.gd.skoda.vehicle.CarType;
import com.gd.skoda.vehicle.DataPart;
import com.gd.skoda.vehicle.DoorsLockedState;
import com.gd.skoda.vehicle.EngineRange;
import com.gd.skoda.vehicle.EngineType;
import com.gd.skoda.vehicle.Freshness;
import com.gd.skoda.vehicle.LightsState;
import com.gd.skoda.vehicle.Location;
import com.gd.skoda.vehicle.LockState;
import com.gd.skoda.vehicle.OpenState;
import com.gd.skoda.vehicle.ParkingState;
import com.gd.skoda.vehicle.Range;
import com.gd.skoda.vehicle.ReliableLockState;
import com.gd.skoda.vehicle.Status;
import com.gd.skoda.vehicle.UnavailabilityReason;
import com.gd.skoda.vehicle.UnavailablePart;
import com.gd.skoda.vehicle.VehicleData;
import com.gd.skoda.vehicle.VehicleOverview;
import java.time.Instant;
import java.util.List;

/** Fixed domain values for the REST, facade and MCP tool tests. */
public final class ApiTestData {

    public static final Instant FETCHED_AT = Instant.parse("2026-10-11T10:00:00Z");

    public static final Location LOCATION = new Location(ParkingState.PARKED, 50.4113, 14.9083,
            "tř. Václava Klementa 869, 293 01 Mladá Boleslav, CZ", null);

    public static final Range RANGE = new Range(CarType.DIESEL, 640,
            new EngineRange(EngineType.DIESEL, 55, null, 640), null, 4000, null,
            Instant.parse("2026-10-11T09:30:00Z"));

    public static final Status STATUS = new Status(LockState.YES, DoorsLockedState.YES, OpenState.CLOSED, OpenState.CLOSED,
            LightsState.OFF, ReliableLockState.LOCKED, OpenState.CLOSED, OpenState.CLOSED, OpenState.UNSUPPORTED,
            48_210L, Instant.parse("2026-10-11T09:29:00Z"));

    public static final UnavailablePart CHARGING_UNSUPPORTED = new UnavailablePart(DataPart.CHARGING,
            UnavailabilityReason.UNSUPPORTED, "CHARGING_UNSUPPORTED", "Charging is not supported");

    private ApiTestData() {
    }

    public static Freshness freshness(boolean stale, UnavailablePart... unavailable) {
        return new Freshness(FETCHED_AT, stale, List.of(unavailable));
    }

    public static VehicleData<VehicleOverview> fullOverview() {
        return new VehicleData<>(new VehicleOverview("Octavia", "1AB 2345", LOCATION, RANGE, STATUS,
                Instant.parse("2026-10-11T09:30:00Z")), freshness(false, CHARGING_UNSUPPORTED));
    }

    public static VehicleData<VehicleOverview> emptyOverview(boolean stale) {
        return new VehicleData<>(new VehicleOverview(null, null, Location.EMPTY, Range.EMPTY, Status.EMPTY, null),
                freshness(stale));
    }

    public static ApiKeyInfo apiKeyInfo() {
        return new ApiKeyInfo(Instant.parse("2026-12-31T00:00:00Z"), 80L, 20, 17,
                Instant.parse("2026-10-11T10:45:00Z"), FETCHED_AT);
    }
}
