package com.gd.api.vehicleaccess.service;

public interface VehicleAccessClientService {

    void flash(String vin);

    void honkAndFlash(String vin);

    void lock(String vin, String spin);

    void unlock(String vin, String spin);
}
