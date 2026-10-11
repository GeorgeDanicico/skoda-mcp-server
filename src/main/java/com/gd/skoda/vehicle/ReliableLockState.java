package com.gd.skoda.vehicle;

/**
 * Reliable lock state ({@code status.overall.reliableLockStatus}); {@code UNLOCKED} is only reported by MOD4 vehicles.
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum ReliableLockState {
    LOCKED, UNLOCKED, UNKNOWN
}
