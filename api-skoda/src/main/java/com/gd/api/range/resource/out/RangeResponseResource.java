package com.gd.api.range.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RangeResponseResource {
    private String carType;
    private int totalRangeInKm;
    private EngineRangeResponseResource primaryEngineRange;
    private String carCapturedTimestamp;
}
