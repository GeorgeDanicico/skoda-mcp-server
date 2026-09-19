package com.gd.api.charging.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChargingResponseResource {
    private boolean isVehicleInSavedLocation;
    private ChargingStatusResponseResource status;
    private ChargingSettingsResponseResource settings;
    private String carCapturedTimestamp;
}
