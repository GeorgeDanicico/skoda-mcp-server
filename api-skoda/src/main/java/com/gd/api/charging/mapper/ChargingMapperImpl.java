package com.gd.api.charging.mapper;

import com.gd.api.charging.domain.ChargingSession;
import com.gd.api.charging.domain.ChargingState;
import com.gd.api.resource.ChargingSessionWebResponseResource;
import com.gd.api.resource.ChargingStatusWebResponseResource;
import org.springframework.stereotype.Component;

@Component
public class ChargingMapperImpl implements ChargingMapper {
    @Override
    public ChargingStatusWebResponseResource toWebResponseResource(ChargingState chargingState) {
        return ChargingStatusWebResponseResource.builder()
                .chargingRateInKilometersPerHour(chargingState.chargingRateInKilometersPerHour())
                .chargePowerInKw(chargingState.chargePowerInKw())
                .remainingTimeToFullyChargedInMinutes(chargingState.remainingTimeToFullyChargedInMinutes())
                .state(chargingState.state())
                .chargeType(chargingState.chargeType())
                .remainingCruisingRangeInMeters(chargingState.remainingCruisingRangeInMeters())
                .stateOfChargeInPercent(chargingState.stateOfChargeInPercent())
                .carCapturedTimestamp(chargingState.carCapturedTimestamp())
                .build();
    }

    @Override
    public ChargingSessionWebResponseResource toWebResponseResource(ChargingSession chargingSession) {
        return ChargingSessionWebResponseResource.builder()
                .startAt(chargingSession.startAt())
                .chargedInKWh(chargingSession.chargedInKWh())
                .durationInMinutes(chargingSession.durationInMinutes())
                .currentType(chargingSession.currentType())
                .build();
    }
}
