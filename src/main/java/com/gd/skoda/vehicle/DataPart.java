package com.gd.skoda.vehicle;

/**
 * A part of the vehicle data that an upstream {@code errors[]} entry refers to: the prefix of the
 * error type, e.g. {@code PARKING_POSITION} in {@code PARKING_POSITION_UNAVAILABLE}. {@code UNKNOWN}
 * stands for error types this version does not recognise.
 */
public enum DataPart {
    RENDER, VEHICLE_STATUS, FUEL_STATUS, ODOMETER, PARKING_POSITION, AIR_CONDITIONING, AUXILIARY_HEATING,
    ACTIVE_VENTILATION, CHARGING, CHARGING_PROFILES, OPERATIONS, UNKNOWN
}
