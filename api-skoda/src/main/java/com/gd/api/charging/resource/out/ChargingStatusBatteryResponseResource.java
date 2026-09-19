package com.gd.api.charging.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChargingStatusBatteryResponseResource {
    private int remainingCruisingRangeInMeters;
    private int stateOfChargeInPercent;
}
