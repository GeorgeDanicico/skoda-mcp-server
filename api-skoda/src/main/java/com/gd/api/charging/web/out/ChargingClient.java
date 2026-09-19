package com.gd.api.charging.web.out;

import com.gd.api.charging.resource.out.ChargingHistoryResponseResource;
import com.gd.api.charging.resource.out.ChargingResponseResource;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface ChargingClient {
    @RequestLine("GET /api/v1/charging/{vin}")
    @Headers({"Accept: application/json"})
    ChargingResponseResource findChargingByVin(@Param("vin") String vin);

    @RequestLine("GET /api/v1/charging/{vin}/history?userTimezone=Europe/Brussels&limit=100")
    @Headers({"Accept: application/json"})
    ChargingHistoryResponseResource findChargingHistoryByVin(@Param("vin") String vin);

    @RequestLine("POST /api/v1/charging/{vin}/start")
    @Headers({"Accept: application/json"})
    void startChargingByVin(@Param("vin") String vin);

    @RequestLine("POST /api/v1/charging/{vin}/stop")
    @Headers({"Accept: application/json"})
    void stopChargingByVin(@Param("vin") String vin);
}
