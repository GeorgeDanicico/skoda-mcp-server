package com.gd.skoda.vehicle;

import com.gd.skoda.upstream.publicapi.model.BatteryStatus;
import com.gd.skoda.upstream.publicapi.model.Charging;
import com.gd.skoda.upstream.publicapi.model.ChargingStatus;
import com.gd.skoda.upstream.publicapi.model.FuelStatus;
import com.gd.skoda.upstream.publicapi.model.Odometer;
import com.gd.skoda.upstream.publicapi.model.OverallVehicleStatus;
import com.gd.skoda.upstream.publicapi.model.ParkingPosition;
import com.gd.skoda.upstream.publicapi.model.ParkingPositionGpsCoordinates;
import com.gd.skoda.upstream.publicapi.model.Vehicle;
import com.gd.skoda.upstream.publicapi.model.VehicleError;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import com.gd.skoda.upstream.publicapi.model.VehicleStatus;
import com.gd.skoda.upstream.publicapi.model.VehicleStatusDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the generated upstream models to the domain records.
 *
 * <p>Lenient by design: missing parts become empty records, missing fields become {@code null}, and
 * unrecognised enum strings become {@code UNKNOWN}. It never throws on upstream content.
 */
public final class VehicleSnapshotMapper {

    private static final Logger log = LoggerFactory.getLogger(VehicleSnapshotMapper.class);

    /**
     * @param response  the upstream response
     * @param fetchedAt when the response was received
     */
    public VehicleSnapshot toSnapshot(VehicleResponse response, Instant fetchedAt) {
        Vehicle vehicle = response.getVehicle();
        Location location = vehicle != null ? location(vehicle.getParkingPosition()) : Location.EMPTY;
        Range range = vehicle != null ? range(vehicle.getFuelStatus(), vehicle.getCharging()) : Range.EMPTY;
        Status status = vehicle != null ? status(vehicle.getStatus(), vehicle.getOdometer()) : Status.EMPTY;
        VehicleOverview overview = new VehicleOverview(
                vehicle != null ? vehicle.getName() : null,
                vehicle != null ? vehicle.getLicensePlate() : null,
                location, range, status,
                latest(location.capturedAt(), range.capturedAt(), status.capturedAt()));
        List<VehicleError> errors = response.getErrors() != null ? response.getErrors() : List.of();
        return new VehicleSnapshot(overview,
                errors.stream().filter(Objects::nonNull).map(VehicleSnapshotMapper::unavailablePart).toList(),
                fetchedAt);
    }

    static Location location(@Nullable ParkingPosition position) {
        if (position == null) {
            return Location.EMPTY;
        }
        ParkingPositionGpsCoordinates gps = position.getGpsCoordinates();
        return new Location(
                parse(position.getState(), ParkingState.UNKNOWN),
                gps != null ? gps.getLatitude() : null,
                gps != null ? gps.getLongitude() : null,
                position.getFormattedAddress(),
                null); // the Public API sends no timestamp for the parking position
    }

    static Range range(@Nullable FuelStatus fuel, @Nullable Charging charging) {
        BatteryStatus battery = battery(charging);
        Integer batterySoc = battery != null ? battery.getStateOfChargeInPercent() : null;
        Instant chargingCapturedAt = charging != null ? instant(charging.getCarCapturedTimestamp()) : null;

        if (fuel == null) {
            if (battery == null) {
                return Range.EMPTY;
            }
            // battery-electric vehicle: no fuel status, range comes from the battery
            Integer batteryRangeKm = metersToKm(battery.getRemainingCruisingRangeInMeters());
            EngineRange electric = new EngineRange(EngineType.ELECTRIC, null, batterySoc, batteryRangeKm);
            return new Range(null, batteryRangeKm, electric, null, null, batterySoc, chargingCapturedAt);
        }

        EngineRange primary = engineRange(fuel.getPrimaryEngineRange());
        EngineRange secondary = engineRange(fuel.getSecondaryEngineRange());
        if (batterySoc == null) {
            batterySoc = Stream.of(primary, secondary)
                    .filter(Objects::nonNull)
                    .filter(engine -> engine.engineType() == EngineType.ELECTRIC && engine.socPercent() != null)
                    .map(EngineRange::socPercent)
                    .findFirst().orElse(null);
        }
        Instant capturedAt = instant(fuel.getCarCapturedTimestamp());
        return new Range(
                parse(fuel.getCarType(), CarType.UNKNOWN),
                round(fuel.getTotalRangeInKm()),
                primary,
                secondary,
                round(fuel.getAdBlueRange()),
                batterySoc,
                capturedAt != null ? capturedAt : chargingCapturedAt);
    }

    static Status status(@Nullable VehicleStatus status, @Nullable Odometer odometer) {
        if (status == null && odometer == null) {
            return Status.EMPTY;
        }
        OverallVehicleStatus overall = status != null ? status.getOverall() : null;
        VehicleStatusDetail detail = status != null ? status.getDetail() : null;
        Instant statusCapturedAt = status != null ? instant(status.getCarCapturedTimestamp()) : null;
        Instant odometerCapturedAt = odometer != null ? instant(odometer.getCarCapturedTimestamp()) : null;
        return new Status(
                overall != null ? parse(overall.getLocked(), LockState.UNKNOWN) : null,
                overall != null ? parse(overall.getDoorsLocked(), DoorsLockedState.UNKNOWN) : null,
                overall != null ? parse(overall.getDoors(), OpenState.UNKNOWN) : null,
                overall != null ? parse(overall.getWindows(), OpenState.UNKNOWN) : null,
                overall != null ? parse(overall.getLights(), LightsState.UNKNOWN) : null,
                overall != null ? parse(overall.getReliableLockStatus(), ReliableLockState.UNKNOWN) : null,
                detail != null ? parse(detail.getTrunk(), OpenState.UNKNOWN) : null,
                detail != null ? parse(detail.getBonnet(), OpenState.UNKNOWN) : null,
                detail != null ? parse(detail.getSunroof(), OpenState.UNKNOWN) : null,
                odometer != null ? odometer.getMileageInKm() : null,
                statusCapturedAt != null ? statusCapturedAt : odometerCapturedAt);
    }

    /** Splits e.g. {@code PARKING_POSITION_UNAVAILABLE} into part and reason. */
    static UnavailablePart unavailablePart(VehicleError error) {
        String type = error.getType();
        DataPart part = DataPart.UNKNOWN;
        UnavailabilityReason reason = UnavailabilityReason.UNKNOWN;
        if (type != null) {
            String normalized = type.strip().toUpperCase(Locale.ROOT);
            int separator = normalized.lastIndexOf('_');
            if (separator > 0) {
                UnavailabilityReason parsedReason =
                        parse(normalized.substring(separator + 1), UnavailabilityReason.UNKNOWN);
                DataPart parsedPart = parse(normalized.substring(0, separator), DataPart.UNKNOWN);
                if (parsedReason != UnavailabilityReason.UNKNOWN && parsedPart != DataPart.UNKNOWN) {
                    part = parsedPart;
                    reason = parsedReason;
                }
            }
            if (part == DataPart.UNKNOWN) {
                log.debug("Unrecognised upstream error type '{}'", type);
            }
        }
        return new UnavailablePart(part, reason, type, error.getDescription());
    }

    private static @Nullable EngineRange engineRange(
            com.gd.skoda.upstream.publicapi.model.@Nullable EngineRange range) {
        if (range == null) {
            return null;
        }
        return new EngineRange(
                parse(range.getEngineType(), EngineType.UNKNOWN),
                round(range.getCurrentFuelLevelInPercent()),
                round(range.getCurrentSoCInPercent()),
                round(range.getRemainingRangeInKm()));
    }

    private static @Nullable BatteryStatus battery(@Nullable Charging charging) {
        ChargingStatus status = charging != null ? charging.getStatus() : null;
        return status != null ? status.getBattery() : null;
    }

    /**
     * Parses an upstream string into an enum constant: {@code null} if absent, {@code unknown} if the
     * value is not recognised.
     */
    static <E extends Enum<E>> @Nullable E parse(@Nullable String value, E unknown) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(unknown.getDeclaringClass(), value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.debug("Unrecognised upstream value '{}' for {}", value, unknown.getDeclaringClass().getSimpleName());
            return unknown;
        }
    }

    private static @Nullable Integer round(@Nullable BigDecimal value) {
        return value != null ? value.setScale(0, RoundingMode.HALF_UP).intValue() : null;
    }

    private static @Nullable Integer metersToKm(@Nullable Integer meters) {
        return meters != null ? Math.round(meters / 1000.0f) : null;
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private static @Nullable Instant latest(@Nullable Instant... instants) {
        return Stream.of(instants).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }
}
