package com.gd.skoda.vehicle;

/**
 * Overall lights state ({@code status.overall.lights}).
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum LightsState {
    ON, OFF, UNKNOWN
}
