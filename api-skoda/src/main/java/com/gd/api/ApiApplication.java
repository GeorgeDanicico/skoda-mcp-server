package com.gd.api;

import com.gd.api.airconditioning.resource.out.AirConditioningResponseResource;
import com.gd.api.airconditioning.resource.out.AirConditioningStartRequestResource;
import com.gd.api.charging.resource.out.ChargingHistoryResponseResource;
import com.gd.api.charging.resource.out.ChargingResponseResource;
import com.gd.api.core.error.ErrorResponse;
import com.gd.api.location.resource.out.ParkResponseResource;
import com.gd.api.range.resource.out.RangeResponseResource;
import com.gd.api.status.resource.out.StatusResponseResource;
import com.gd.api.vehicle.resource.out.VehicleGarageResponseResource;
import com.gd.api.vehicleaccess.resource.out.HonkAndFlashRequestResource;
import com.gd.api.vehicleaccess.resource.out.VehicleAccessSecurityRequestResource;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(ApiRuntimeHints.class)
@RegisterReflectionForBinding(value = {
        AirConditioningResponseResource.class,
        AirConditioningStartRequestResource.class,
        ChargingResponseResource.class,
        ChargingHistoryResponseResource.class,
        ParkResponseResource.class,
        RangeResponseResource.class,
        StatusResponseResource.class,
        VehicleGarageResponseResource.class,
        HonkAndFlashRequestResource.class,
        VehicleAccessSecurityRequestResource.class,
        ErrorResponse.class
})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

}
