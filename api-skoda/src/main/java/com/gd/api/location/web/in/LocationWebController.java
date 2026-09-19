package com.gd.api.location.web.in;

import com.gd.api.controller.LocationApi;
import com.gd.api.location.mapper.LocationMapper;
import com.gd.api.location.service.LocationService;
import com.gd.api.resource.LocationWebResponseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class LocationWebController implements LocationApi {

    private final LocationMapper mapper;
    private final LocationService service;

    @Override
    public ResponseEntity<LocationWebResponseResource> getLocation(String vin) {
        return ResponseEntity.ok(mapper.toWebResource(service.findLocationByVin(vin)));
    }
}
