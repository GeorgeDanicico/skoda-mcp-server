package com.gd.skoda.api;

import com.gd.skoda.api.generated.VehicleApi;
import com.gd.skoda.api.generated.model.VehicleLocationResponseDto;
import com.gd.skoda.api.generated.model.VehicleOverviewResponseDto;
import com.gd.skoda.api.generated.model.VehicleRangeResponseDto;
import com.gd.skoda.api.generated.model.VehicleStatusResponseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** REST adapter for the generated {@link VehicleApi}; all logic lives in {@link VehicleApiFacade}. */
@RestController
@Profile("api")
class VehicleApiController implements VehicleApi {

    private final VehicleApiFacade facade;

    VehicleApiController(VehicleApiFacade facade) {
        this.facade = facade;
    }

    @Override
    public ResponseEntity<VehicleOverviewResponseDto> getVehicleOverview() {
        return ApiResponses.ok(facade.getVehicleOverview());
    }

    @Override
    public ResponseEntity<VehicleLocationResponseDto> getVehicleLocation() {
        return ApiResponses.ok(facade.getVehicleLocation());
    }

    @Override
    public ResponseEntity<VehicleRangeResponseDto> getVehicleRange() {
        return ApiResponses.ok(facade.getVehicleRange());
    }

    @Override
    public ResponseEntity<VehicleStatusResponseDto> getVehicleStatus() {
        return ApiResponses.ok(facade.getVehicleStatus());
    }
}
