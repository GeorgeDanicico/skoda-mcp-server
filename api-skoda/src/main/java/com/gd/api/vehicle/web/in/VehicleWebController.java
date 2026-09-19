package com.gd.api.vehicle.web.in;

import com.gd.api.controller.VehicleApi;
import com.gd.api.resource.VehicleWebResponseResource;
import com.gd.api.vehicle.mapper.VehicleMapper;
import com.gd.api.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class VehicleWebController implements VehicleApi {

    private final VehicleMapper mapper;
    private final VehicleService service;

    @Override
    public ResponseEntity<List<VehicleWebResponseResource>> findAllVehicles() {
        return ResponseEntity.ok(mapper.toWebResource(service.findAllVehicles()));
    }
}
