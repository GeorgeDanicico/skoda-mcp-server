package com.gd.api.charging.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChargingStatusResponseResource {
    private double chargingRateInKilometersPerHour;
    private double chargePowerInKw;
    private int remainingTimeToFullyChargedInMinutes;
    private String fullyChargedAt;
    private String state;
    private String chargeType;
    private ChargingStatusBatteryResponseResource battery;
}
