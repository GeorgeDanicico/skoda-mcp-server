package com.gd.api.location.mapper;

import com.gd.api.location.domain.Location;
import com.gd.api.resource.LocationWebResponseResource;

public interface LocationMapper {
    LocationWebResponseResource toWebResource(final Location resource);
}
