package com.gd.api.charging.service;

import com.gd.api.charging.domain.ChargingSession;
import com.gd.api.charging.domain.ChargingState;

import java.util.List;

public interface ChargingClientService {

    ChargingState findChargingStateByVin(String vin);

    List<ChargingSession> getChargingSessionsByVin(String vin);

    void startChargingByVin(String vin);

    void stopChargingByVin(String vin);
}
