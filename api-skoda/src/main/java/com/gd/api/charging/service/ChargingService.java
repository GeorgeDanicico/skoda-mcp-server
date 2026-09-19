package com.gd.api.charging.service;

import com.gd.api.charging.domain.ChargingSession;
import com.gd.api.charging.domain.ChargingState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChargingService {

    private final ChargingClientService service;

    public ChargingState findChargingStateByVin(String vin) {
        log.info("Get charging state for VIN: {}", vin);
        return service.findChargingStateByVin(vin);
    }

    public List<ChargingSession> findAllChargingSessionsByVin(String vin) {
        log.info("Get charging sessions for VIN: {}", vin);
        return service.getChargingSessionsByVin(vin);
    }

    public void startCharging(String vin) {
        log.info("Starting charging for VIN: {}", vin);
        service.startChargingByVin(vin);
    }

    public void stopCharging(String vin) {
        log.info("Stopping charging for VIN: {}", vin);
        service.stopChargingByVin(vin);
    }

}
