package com.gd.api.status.service;

import com.gd.api.status.domain.Status;

public interface StatusClientService {
    Status getStatusByVin(String vin);
}
