package com.gd.api.vehicleaccess.web.out;

import com.gd.api.vehicleaccess.resource.out.HonkAndFlashRequestResource;
import com.gd.api.vehicleaccess.resource.out.VehicleAccessSecurityRequestResource;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface VehicleAccessClient {

    @RequestLine("POST /api/v1/vehicle-access/{vin}/honk-and-flash")
    @Headers({"Accept: application/json", "Content-Type: application/json"})
    void honkAndFlash(@Param("vin") String vin, HonkAndFlashRequestResource request);

    @RequestLine("POST /api/v1/vehicle-access/{vin}/lock")
    @Headers({"Accept: application/json", "Content-Type: application/json"})
    void lock(@Param("vin") String vin, VehicleAccessSecurityRequestResource request);

    @RequestLine("POST /api/v1/vehicle-access/{vin}/unlock")
    @Headers({"Accept: application/json", "Content-Type: application/json"})
    void unlock(@Param("vin") String vin, VehicleAccessSecurityRequestResource request);
}
