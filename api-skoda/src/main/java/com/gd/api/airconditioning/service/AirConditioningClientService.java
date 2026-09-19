package com.gd.api.airconditioning.service;

import com.gd.api.airconditioning.domain.AirConditioning;

public interface AirConditioningClientService {

    AirConditioning findAirConditioningByVin(String vin);

    void startAirConditioning(String vin, AirConditioning airConditioning);

    void stopAirConditioning(String vin);
}
