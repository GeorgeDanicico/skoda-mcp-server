package com.gd.api.charging.mapper;

import com.gd.api.charging.domain.ChargingSession;
import com.gd.api.charging.domain.ChargingState;
import com.gd.api.resource.ChargingSessionWebResponseResource;
import com.gd.api.resource.ChargingStatusWebResponseResource;

public interface ChargingMapper {

    ChargingStatusWebResponseResource toWebResponseResource(ChargingState chargingState);

    ChargingSessionWebResponseResource toWebResponseResource(ChargingSession chargingSession);
}
