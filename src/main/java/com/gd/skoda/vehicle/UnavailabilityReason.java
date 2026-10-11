package com.gd.skoda.vehicle;

/**
 * Why a part of the vehicle data is missing: the suffix of the upstream error type, e.g.
 * {@code UNAVAILABLE} in {@code PARKING_POSITION_UNAVAILABLE}. {@code UNKNOWN} stands for suffixes
 * this version does not recognise.
 */
public enum UnavailabilityReason {
    /** The vehicle does not support the part. */
    UNSUPPORTED,
    /** The vehicle supports the part, but it is currently disabled. */
    DISABLED,
    /** The part could not be retrieved this time. */
    UNAVAILABLE,
    UNKNOWN
}
