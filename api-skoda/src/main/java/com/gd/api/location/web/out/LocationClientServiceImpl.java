package com.gd.api.location.web.out;

import com.gd.api.location.domain.Location;
import com.gd.api.location.resource.out.ParkResponseResource;
import com.gd.api.location.service.LocationClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class LocationClientServiceImpl implements LocationClientService {

    private final LocationClient client;

    @Override
    public Location findLocationByVin(String vin) {
        log.info("find location for vin {}", vin);
        ParkResponseResource responseResource = client.findPositionByVin(vin);
        return new Location(responseResource.getParkingPosition().getGpsCoordinates().getLatitude(),
                responseResource.getParkingPosition().getGpsCoordinates().getLongitude(),
                responseResource.getParkingPosition().getFormattedAddress());
    }
}
