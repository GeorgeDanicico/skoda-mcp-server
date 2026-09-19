package com.gd.api.range.resource.out;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngineRangeResponseResource {
    private String engineType;
    private int currentSoCInPercent;
    private int remainingRangeInKm;
}
