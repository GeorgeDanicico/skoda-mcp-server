package com.gd.api.vehicleaccess.web.in;

import com.gd.api.controller.VehicleAccessApi;
import com.gd.api.resource.VehicleAccessSecurityWebRequestResource;
import com.gd.api.vehicleaccess.service.VehicleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class VehicleAccessWebController implements VehicleAccessApi {

    private final VehicleAccessService service;

    @Override
    public ResponseEntity<Void> flash(String vin) {
        service.flash(vin);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> honkAndFlash(String vin) {
        service.honkAndFlash(vin);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> lock(String vin, VehicleAccessSecurityWebRequestResource request) {
        service.lock(vin, request.getSpin());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> unlock(String vin, VehicleAccessSecurityWebRequestResource request) {
        service.unlock(vin, request.getSpin());
        return ResponseEntity.accepted().build();
    }
}
