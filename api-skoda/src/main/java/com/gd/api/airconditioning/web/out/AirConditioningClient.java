package com.gd.api.airconditioning.web.out;

import com.gd.api.airconditioning.resource.out.AirConditioningResponseResource;
import com.gd.api.airconditioning.resource.out.AirConditioningStartRequestResource;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import org.springframework.web.bind.annotation.RequestBody;

public interface AirConditioningClient {
    @RequestLine("GET /api/v2/air-conditioning/{vin}")
    @Headers({"Accept: application/json"})
    AirConditioningResponseResource findAirConditioningByVin(@Param("vin") String vin);

    @RequestLine("GET /api/v2/air-conditioning/{vin}/start")
    @Headers({"Accept: application/json", "Content-Type: application/json"})
    void startAirConditioningByVin(@Param("vin") String vin, @RequestBody AirConditioningStartRequestResource requestBody);

    @RequestLine("GET /api/v2/air-conditioning/{vin}/start")
    @Headers({"Accept: application/json"})
    void stopAirConditioningByVin(@Param("vin") String vin);
}
