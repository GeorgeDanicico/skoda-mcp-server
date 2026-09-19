package com.gd.api.range.mapper;

import com.gd.api.range.domain.Range;
import com.gd.api.resource.RangeWebResponseResource;

public interface RangeMapper {

    RangeWebResponseResource toWebResource(final Range resource);
}
