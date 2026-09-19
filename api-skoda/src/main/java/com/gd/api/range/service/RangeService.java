package com.gd.api.range.service;

import com.gd.api.range.domain.Range;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RangeService {
    private final RangeClientService service;

    public Range findRangeByVin(String vin) {
        return service.findRangeByVin(vin);
    }
}
