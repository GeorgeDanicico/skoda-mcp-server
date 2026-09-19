package com.gd.api.vehicleaccess.web.out;

import com.gd.api.location.domain.Location;
import com.gd.api.location.service.LocationClientService;
import com.gd.api.vehicleaccess.resource.out.HonkAndFlashRequestResource;
import com.gd.api.vehicleaccess.resource.out.VehicleAccessSecurityRequestResource;
import com.gd.api.vehicleaccess.resource.out.VehiclePositionRequestResource;
import com.gd.api.vehicleaccess.service.VehicleAccessClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class VehicleAccessClientServiceImpl implements VehicleAccessClientService {

    private static final String FLASH_MODE = "FLASH";
    private static final String HONK_AND_FLASH_MODE = "HONK_AND_FLASH";

    private final VehicleAccessClient client;
    private final LocationClientService locationClientService;

    @Override
    public void flash(String vin) {
        log.info("send flash command for vin {}", vin);
        client.honkAndFlash(vin, createHonkAndFlashRequest(vin, FLASH_MODE));
    }

    @Override
    public void honkAndFlash(String vin) {
        log.info("send honk and flash command for vin {}", vin);
        client.honkAndFlash(vin, createHonkAndFlashRequest(vin, HONK_AND_FLASH_MODE));
    }

    @Override
    public void lock(String vin, String spin) {
        log.info("send lock command for vin {}", vin);
        client.lock(vin, new VehicleAccessSecurityRequestResource(spin));
    }

    @Override
    public void unlock(String vin, String spin) {
        log.info("send unlock command for vin {}", vin);
        client.unlock(vin, new VehicleAccessSecurityRequestResource(spin));
    }

    private HonkAndFlashRequestResource createHonkAndFlashRequest(String vin, String mode) {
        Location location = locationClientService.findLocationByVin(vin);
        VehiclePositionRequestResource position = new VehiclePositionRequestResource(
                location.latitude(), location.longitude());
        return new HonkAndFlashRequestResource(mode, position);
    }
}
