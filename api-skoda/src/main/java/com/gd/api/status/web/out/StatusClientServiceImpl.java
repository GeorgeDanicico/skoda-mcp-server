package com.gd.api.status.web.out;

import com.gd.api.status.domain.Status;
import com.gd.api.status.resource.out.StatusResponseResource;
import com.gd.api.status.service.StatusClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StatusClientServiceImpl implements StatusClientService {

    private final StatusClient client;

    @Override
    public Status getStatusByVin(String vin) {
        log.info("get status for vin {}", vin);
        StatusResponseResource response = client.getStatus(vin);
        return new Status(
                response.getOverall().getDoorsLocked(),
                response.getOverall().getLocked(),
                response.getOverall().getDoors(),
                response.getOverall().getWindows(),
                response.getOverall().getLights(),
                response.getOverall().getReliableLockStatus(),
                response.getDetail().getSunroof(),
                response.getDetail().getTrunk(),
                response.getDetail().getBonnet(),
                response.getCarCapturedTimestamp()
        );
    }
}
