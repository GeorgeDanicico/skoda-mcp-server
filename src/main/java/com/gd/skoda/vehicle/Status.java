package com.gd.skoda.vehicle;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Lock, door, window and light state plus the odometer. Every component is {@code null} while unknown.
 *
 * @param locked             all supported doors and the trunk are locked and closed
 * @param doorsLocked        doors and trunk lock state, distinguishing open doors and an open trunk
 * @param doors              any door open
 * @param windows            any side window open (sunroof excluded)
 * @param lights             any light on
 * @param reliableLockStatus lock state as reported by MOD4 vehicles
 * @param trunk              trunk open
 * @param bonnet             bonnet open
 * @param sunroof            sunroof open, or {@link OpenState#UNSUPPORTED}
 * @param odometerKm         mileage
 * @param capturedAt         when the vehicle reported the status (the odometer timestamp if no
 *                           status was delivered)
 */
public record Status(
        @Nullable LockState locked,
        @Nullable DoorsLockedState doorsLocked,
        @Nullable OpenState doors,
        @Nullable OpenState windows,
        @Nullable LightsState lights,
        @Nullable ReliableLockState reliableLockStatus,
        @Nullable OpenState trunk,
        @Nullable OpenState bonnet,
        @Nullable OpenState sunroof,
        @Nullable Long odometerKm,
        @Nullable Instant capturedAt) {

    /** No status data at all. */
    public static final Status EMPTY = new Status(null, null, null, null, null, null, null, null, null, null, null);
}
