package com.gd.skoda.mcp;

/** Safe, transport-independent representation of the configured vehicle. */
public record VehicleSnapshot(
        String vehicleName,
        String licensePlate,
        Integer rangeKm,
        Integer batteryPercent,
        String doorsLocked,
        String doors,
        String windows,
        String trunk,
        String bonnet,
        String lights,
        Location location,
        String capturedAt,
        boolean partial,
        java.util.List<String> unavailableSections) {

    public record Location(Double latitude, Double longitude, String address) {}
}
