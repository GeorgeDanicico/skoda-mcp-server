package com.gd.skoda.vehicle;

/**
 * Open/closed state of doors, windows, trunk, bonnet or sunroof.
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum OpenState {
    OPEN, CLOSED, UNSUPPORTED, UNKNOWN
}
