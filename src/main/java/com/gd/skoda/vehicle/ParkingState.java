package com.gd.skoda.vehicle;

/**
 * Whether the vehicle is parked, per {@code parkingPosition.state}.
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum ParkingState {
    PARKED, IN_MOTION, UNKNOWN
}
