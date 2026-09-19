package com.gd.api.range.web.in;

import com.gd.api.controller.RangeApi;
import com.gd.api.range.mapper.RangeMapper;
import com.gd.api.range.service.RangeService;
import com.gd.api.resource.RangeWebResponseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class RangeWebController implements RangeApi {

    private final RangeMapper mapper;
    private final RangeService service;


    @Override
    public ResponseEntity<RangeWebResponseResource> getRange(String vin) {
        return ResponseEntity.ok(mapper.toWebResource(service.findRangeByVin(vin)));
    }
}
