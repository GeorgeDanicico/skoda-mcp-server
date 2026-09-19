package com.gd.api.airconditioning.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirConditioningStartRequestResource {
    private String heaterSource;
    private AirConditioningTargetTemperatureStartRequestResource targetTemperature;
}
