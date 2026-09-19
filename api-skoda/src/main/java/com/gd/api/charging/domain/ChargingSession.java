package com.gd.api.charging.domain;

public record ChargingSession(String startAt, double chargedInKWh, int durationInMinutes, String currentType) {
}