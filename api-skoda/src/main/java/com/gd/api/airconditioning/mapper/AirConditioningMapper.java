package com.gd.api.airconditioning.mapper;

import com.gd.api.airconditioning.domain.AirConditioning;
import com.gd.api.resource.AirConditioningWebRequestResource;
import com.gd.api.resource.AirConditioningWebResponseResource;

public interface AirConditioningMapper {

    AirConditioningWebResponseResource toWebResource(final AirConditioning resource);

    AirConditioning toDomain(final AirConditioningWebRequestResource resource);

}
