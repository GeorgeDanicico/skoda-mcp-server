package com.gd.api.airconditioning.web.in;

import com.gd.api.airconditioning.mapper.AirConditioningMapper;
import com.gd.api.airconditioning.service.AirConditioningService;
import com.gd.api.controller.AirConditioningApi;
import com.gd.api.resource.AirConditioningWebRequestResource;
import com.gd.api.resource.AirConditioningWebResponseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class AirConditioningWebController implements AirConditioningApi {

    private final AirConditioningMapper mapper;
    private final AirConditioningService service;


    @Override
    public ResponseEntity<AirConditioningWebResponseResource> getAirConditioning(String vin) {
        return ResponseEntity.ok(mapper.toWebResource(service.getAirConditioning(vin)));
    }

    @Override
    public ResponseEntity<Void> startAirConditioning(String vin, AirConditioningWebRequestResource airConditioningWebRequestResource) {
        service.startAirConditioning(vin, mapper.toDomain(airConditioningWebRequestResource));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> stopAirConditioning(String vin) {
        service.stopAirConditioning(vin);
        return ResponseEntity.accepted().build();
    }
}
