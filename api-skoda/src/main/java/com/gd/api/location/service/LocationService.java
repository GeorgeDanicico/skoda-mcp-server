package com.gd.api.location.service;

import com.gd.api.location.domain.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class LocationService {

    private final LocationClientService service;

    public Location findLocationByVin(String vin) {
        log.info("find location by {}", vin);
        return service.findLocationByVin(vin);
    }
}
