package com.gd.api.range.service;

import com.gd.api.range.domain.Range;

public interface RangeClientService {

    Range findRangeByVin(String vin);
}
