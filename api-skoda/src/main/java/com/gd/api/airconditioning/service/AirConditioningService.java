package com.gd.api.airconditioning.service;

import com.gd.api.airconditioning.domain.AirConditioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@RequiredArgsConstructor
@Service
public class AirConditioningService {

    private final AirConditioningClientService service;

    public AirConditioning getAirConditioning(String vin) {
        log.info("Get air conditioning for VIN: {}", vin);
        return service.findAirConditioningByVin(vin);
    }

    public void startAirConditioning(String vin, AirConditioning airConditioning) {
        log.info("Starting air conditioning for VIN: {}", vin);
        service.startAirConditioning(vin, airConditioning);
    }

    public void stopAirConditioning(String vin) {
        log.info("Stopping air conditioning for VIN: {}", vin);
        service.stopAirConditioning(vin);
    }
}
