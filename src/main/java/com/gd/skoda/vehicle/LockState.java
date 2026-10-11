package com.gd.skoda.vehicle;

/**
 * Overall lock state ({@code status.overall.locked}).
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum LockState {
    YES, NO, UNKNOWN
}
