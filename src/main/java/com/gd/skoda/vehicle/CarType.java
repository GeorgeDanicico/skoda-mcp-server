package com.gd.skoda.vehicle;

/**
 * Vehicle type ({@code fuelStatus.carType}). The upstream list is open-ended.
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum CarType {
    HYBRID, GASOLINE, DIESEL, CNG, LPG, UNKNOWN
}
