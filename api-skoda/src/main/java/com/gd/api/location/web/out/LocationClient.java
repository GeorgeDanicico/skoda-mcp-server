package com.gd.api.location.web.out;

import com.gd.api.location.resource.out.ParkResponseResource;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface LocationClient {
    @RequestLine("GET /api/v3/maps/positions/vehicles/{vin}/parking")
    @Headers({"Accept: application/json"})
    ParkResponseResource findPositionByVin(@Param("vin") String vin);
}
