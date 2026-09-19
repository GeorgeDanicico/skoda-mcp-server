package com.gd.api.charging.web.in;

import com.gd.api.charging.domain.ChargingSession;
import com.gd.api.charging.domain.ChargingState;
import com.gd.api.charging.mapper.ChargingMapper;
import com.gd.api.charging.service.ChargingService;
import com.gd.api.controller.ChargingApi;
import com.gd.api.resource.ChargingSessionWebResponseResource;
import com.gd.api.resource.ChargingStatusWebResponseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class ChargingWebController implements ChargingApi {

    private final ChargingMapper mapper;
    private final ChargingService service;

    @Override
    public ResponseEntity<List<ChargingSessionWebResponseResource>> getChargingSession(String vin) {
        List<ChargingSession> sessions = service.findAllChargingSessionsByVin(vin);
        return ResponseEntity.ok(sessions.stream()
                .map(mapper::toWebResponseResource)
                .toList());
    }

    @Override
    public ResponseEntity<ChargingStatusWebResponseResource> getChargingStatus(String vin) {
        ChargingState chargingState = service.findChargingStateByVin(vin);
        return ResponseEntity.ok(mapper.toWebResponseResource(chargingState));
    }

    @Override
    public ResponseEntity<Void> startCharging(String vin) {
        service.startCharging(vin);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> stopCharging(String vin) {
        service.stopCharging(vin);
        return ResponseEntity.accepted().build();
    }
}
