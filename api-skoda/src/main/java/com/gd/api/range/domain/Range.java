package com.gd.api.range.domain;

public record Range(String carType, int totalRangeInKm, String engineType, int currentSoCInPercent, int remainingRangeInKm,
                    String carCapturedTimestamp) {
}
