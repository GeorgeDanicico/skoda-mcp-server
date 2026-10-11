package com.gd.skoda.vehicle;

/**
 * Overall doors and trunk lock state ({@code status.overall.doorsLocked}).
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum DoorsLockedState {
    YES, NO, OPENED, TRUNK_OPENED, UNKNOWN
}
