package com.gd.skoda.api;

import com.gd.skoda.api.generated.SystemApi;
import com.gd.skoda.api.generated.model.ApiKeyInfoDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** REST adapter for the generated {@link SystemApi}; all logic lives in {@link VehicleApiFacade}. */
@RestController
@Profile("api")
class SystemApiController implements SystemApi {

    private final VehicleApiFacade facade;

    SystemApiController(VehicleApiFacade facade) {
        this.facade = facade;
    }

    @Override
    public ResponseEntity<ApiKeyInfoDto> getApiKeyStatus() {
        return ApiResponses.ok(facade.getApiKeyStatus());
    }
}
