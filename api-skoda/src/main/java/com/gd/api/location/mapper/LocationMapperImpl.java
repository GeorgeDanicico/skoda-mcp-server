package com.gd.api.location.mapper;

import com.gd.api.location.domain.Location;
import com.gd.api.resource.LocationWebResponseResource;
import org.springframework.stereotype.Component;

@Component
public class LocationMapperImpl implements LocationMapper {
    @Override
    public LocationWebResponseResource toWebResource(final Location location) {
        return LocationWebResponseResource.builder()
                .latitude(location.latitude())
                .longitude(location.longitude())
                .address(location.address())
                .build();
    }
}
