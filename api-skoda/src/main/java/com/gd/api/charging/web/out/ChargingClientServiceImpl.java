package com.gd.api.charging.web.out;

import com.gd.api.charging.domain.ChargingSession;
import com.gd.api.charging.domain.ChargingState;
import com.gd.api.charging.resource.out.ChargingHistorySessionResponseResource;
import com.gd.api.charging.resource.out.ChargingResponseResource;
import com.gd.api.charging.service.ChargingClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChargingClientServiceImpl implements ChargingClientService {

    private final ChargingClient client;

    @Override
    public ChargingState findChargingStateByVin(String vin) {
        log.info("find charging state for vin {}", vin);
        ChargingResponseResource responseResource = client.findChargingByVin(vin);
        return new ChargingState(responseResource.getStatus().getChargingRateInKilometersPerHour(), responseResource.getStatus().getChargePowerInKw(),
                responseResource.getStatus().getRemainingTimeToFullyChargedInMinutes(), responseResource.getStatus().getState(),
                responseResource.getStatus().getChargeType(),
                responseResource.getStatus().getBattery().getRemainingCruisingRangeInMeters(),
                responseResource.getStatus().getBattery().getStateOfChargeInPercent(),
                responseResource.getCarCapturedTimestamp());
    }

    @Override
    public List<ChargingSession> getChargingSessionsByVin(String vin) {
        log.info("find charging sessions for vin {}", vin);
        List<ChargingHistorySessionResponseResource> sessions = client.findChargingHistoryByVin(vin).getPeriods().getSessions();
        return sessions.stream()
                .map(session -> new ChargingSession(session.getStartAt(), session.getChargedInKWh(), session.getDurationInMinutes(), session.getCurrentType()))
                .toList();
    }

    @Override
    public void startChargingByVin(String vin) {
        log.info("start charging for vin {}", vin);
        client.startChargingByVin(vin);
    }

    @Override
    public void stopChargingByVin(String vin) {
        log.info("stop charging for vin {}", vin);
        client.stopChargingByVin(vin);
    }
}
