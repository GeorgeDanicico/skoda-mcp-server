package com.gd.api.status.web.in;

import com.gd.api.controller.StatusApi;
import com.gd.api.resource.StatusWebResponseResource;
import com.gd.api.status.domain.Status;
import com.gd.api.status.mapper.StatusMapper;
import com.gd.api.status.service.StatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
public class StatusWebController implements StatusApi {
    private final StatusMapper mapper;
    private final StatusService service;

    @Override
    public ResponseEntity<StatusWebResponseResource> getStatus(String vin) {
        Status status = service.getStatus(vin);
        return ResponseEntity.ok(mapper.toWebResource(status));
    }
}
