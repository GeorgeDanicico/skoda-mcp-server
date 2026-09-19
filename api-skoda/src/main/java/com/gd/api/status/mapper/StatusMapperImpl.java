package com.gd.api.status.mapper;

import com.gd.api.resource.StatusWebResponseResource;
import com.gd.api.status.domain.Status;
import org.springframework.stereotype.Component;


@Component
public class StatusMapperImpl implements StatusMapper {
    @Override
    public StatusWebResponseResource toWebResource(final Status status) {
        return StatusWebResponseResource.builder()
                .doorsLocked(status.doorsLocked())
                .locked(status.locked())
                .doors(status.doors())
                .windows(status.windows())
                .lights(status.lights())
                .reliableLockStatus(status.reliableLockStatus())
                .sunroof(status.sunroof())
                .trunk(status.trunk())
                .bonnet(status.bonnet())
                .carCapturedTimestamp(status.carCapturedTimestamp())
                .build();
    }
}
