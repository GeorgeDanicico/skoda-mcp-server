package com.gd.api.airconditioning.domain;

public record AirConditioning(String state, String heaterSource, double temperature,
                              String temperatureUnit, String carCapturedTimestamp) {
}
