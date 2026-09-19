package com.gd.api.airconditioning.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirConditioningTargetTemperatureStartRequestResource {
    private double temperatureValue;
    private String unitInCar;
}
