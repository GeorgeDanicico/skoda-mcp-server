package com.gd.api.status.service;

import com.gd.api.status.domain.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StatusService {

    private final StatusClientService service;

    public Status getStatus(String vin) {
        log.info("get status for {}", vin);
        return service.getStatusByVin(vin);
    }
}
