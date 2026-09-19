package com.gd.api.charging.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChargingHistorySessionResponseResource {
    private String startAt;
    private double chargedInKWh;
    private int durationInMinutes;
    private String currentType;
}
