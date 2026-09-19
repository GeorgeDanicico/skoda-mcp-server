package com.gd.api.charging.resource.out;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChargingHistoryPeriodResponseResource {
    private double totalChargedInKWh;
    private List<ChargingHistorySessionResponseResource> sessions;
}
