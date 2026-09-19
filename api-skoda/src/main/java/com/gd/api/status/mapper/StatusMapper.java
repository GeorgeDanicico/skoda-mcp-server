package com.gd.api.status.mapper;

import com.gd.api.resource.StatusWebResponseResource;
import com.gd.api.status.domain.Status;

public interface StatusMapper {
    StatusWebResponseResource toWebResource(final Status status);
}
