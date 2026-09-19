package com.gd.api.range.mapper;

import com.gd.api.range.domain.Range;
import com.gd.api.resource.RangeWebResponseResource;
import org.springframework.stereotype.Component;

@Component
public class RangeMapperImpl implements RangeMapper {
    @Override
    public RangeWebResponseResource toWebResource(Range resource) {
        return RangeWebResponseResource.builder()
                .carType(resource.carType())
                .totalRangeInKm(resource.totalRangeInKm())
                .engineType(resource.engineType())
                .currentSoCInPercent(resource.currentSoCInPercent())
                .remainingRangeInKm(resource.remainingRangeInKm())
                .carCapturedTimestamp(resource.carCapturedTimestamp())
                .build();
    }
}
