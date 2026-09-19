package com.gd.api.airconditioning.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirConditioningResponseResource {
    private String state;
    private AirConditioningTargetTemperatureResponseResource targetTemperature;
    private String carCapturedTimestamp;
}
