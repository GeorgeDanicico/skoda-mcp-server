package com.gd.skoda.vehicle;

/**
 * Engine type of an engine range. The upstream list is open-ended.
 *
 * <p>{@code UNKNOWN} covers both the upstream value {@code UNKNOWN} and values this version does not
 * recognise; a value that was not reported at all is {@code null} in the domain records.
 */
public enum EngineType {
    ELECTRIC, GASOLINE, DIESEL, CNG, LPG, UNKNOWN
}
