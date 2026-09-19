package com.gd.api.charging.domain;

public record ChargingState(double chargingRateInKilometersPerHour, double chargePowerInKw, int remainingTimeToFullyChargedInMinutes,
                            String state, String chargeType, int remainingCruisingRangeInMeters, int stateOfChargeInPercent,
                            String carCapturedTimestamp) {
}
