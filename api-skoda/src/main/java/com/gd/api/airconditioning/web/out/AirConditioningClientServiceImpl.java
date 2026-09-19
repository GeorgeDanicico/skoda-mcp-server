package com.gd.api.airconditioning.web.out;

import com.gd.api.airconditioning.domain.AirConditioning;
import com.gd.api.airconditioning.resource.out.AirConditioningResponseResource;
import com.gd.api.airconditioning.resource.out.AirConditioningStartRequestResource;
import com.gd.api.airconditioning.resource.out.AirConditioningTargetTemperatureStartRequestResource;
import com.gd.api.airconditioning.service.AirConditioningClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AirConditioningClientServiceImpl implements AirConditioningClientService {

    private final AirConditioningClient client;

    @Override
    public AirConditioning findAirConditioningByVin(String vin) {
        log.info("find air conditioning for vin {}", vin);
        AirConditioningResponseResource responseResource = client.findAirConditioningByVin(vin);
        return new AirConditioning(responseResource.getState(), "", responseResource.getTargetTemperature().getTemperatureValue(),
                responseResource.getTargetTemperature().getUnitInCar(), responseResource.getCarCapturedTimestamp());
    }

    @Override
    public void startAirConditioning(String vin, AirConditioning airConditioning) {
        log.info("start air conditioning for vin {}", vin);
        AirConditioningTargetTemperatureStartRequestResource targetTemperatureRequestResource = new AirConditioningTargetTemperatureStartRequestResource();
        targetTemperatureRequestResource.setTemperatureValue(airConditioning.temperature());
        targetTemperatureRequestResource.setUnitInCar(airConditioning.temperatureUnit());

        AirConditioningStartRequestResource requestResource = new AirConditioningStartRequestResource();
        requestResource.setHeaterSource(airConditioning.heaterSource());
        requestResource.setTargetTemperature(targetTemperatureRequestResource);
        client.startAirConditioningByVin(vin, requestResource);
    }

    @Override
    public void stopAirConditioning(String vin) {
        log.info("stop air conditioning for vin {}", vin);
        client.stopAirConditioningByVin(vin);
    }
}
