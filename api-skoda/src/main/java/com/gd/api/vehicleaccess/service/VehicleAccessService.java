package com.gd.api.vehicleaccess.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class VehicleAccessService {

    private final VehicleAccessClientService service;

    public void flash(String vin) {
        log.info("flash vehicle lights for vin {}", vin);
        service.flash(vin);
    }

    public void honkAndFlash(String vin) {
        log.info("honk and flash vehicle for vin {}", vin);
        service.honkAndFlash(vin);
    }

    public void lock(String vin, String spin) {
        log.info("lock vehicle for vin {}", vin);
        service.lock(vin, spin);
    }

    public void unlock(String vin, String spin) {
        log.info("unlock vehicle for vin {}", vin);
        service.unlock(vin, spin);
    }
}
