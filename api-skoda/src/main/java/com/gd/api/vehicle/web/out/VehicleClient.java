package com.gd.api.vehicle.web.out;

import com.gd.api.vehicle.resource.out.VehicleGarageResponseResource;
import feign.Headers;
import feign.RequestLine;

public interface VehicleClient {
    @RequestLine("GET /api/v2/garage")
    @Headers({"Content-Type: application/json"})
    VehicleGarageResponseResource getVehicles();
}
