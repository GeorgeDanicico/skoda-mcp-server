package com.gd.api.vehicle.service;

import com.gd.api.vehicle.domain.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class VehicleService {

    private final VehicleClientService service;

    public List<Vehicle> findAllVehicles() {
        log.info("find all vehicles");
        return service.getVehicles();
    }
}
