package com.gd.api.location.service;

import com.gd.api.location.domain.Location;

public interface LocationClientService {

    Location findLocationByVin(String vin);
}
