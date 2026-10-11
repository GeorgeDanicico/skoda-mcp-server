package com.gd.skoda.config;

import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.PublicApiClient;
import com.gd.skoda.vehicle.SnapshotCache;
import com.gd.skoda.vehicle.VehicleService;
import com.gd.skoda.vehicle.VehicleSnapshotMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the cached, rate-limit-aware {@link VehicleService} used by both REST and MCP. */
@Configuration(proxyBeanMethods = false)
public class VehicleServiceConfig {

    @Bean
    VehicleSnapshotMapper vehicleSnapshotMapper() {
        return new VehicleSnapshotMapper();
    }

    @Bean
    SnapshotCache snapshotCache(PublicApiClient publicApiClient, VehicleSnapshotMapper vehicleSnapshotMapper,
            ApiKeyStatus apiKeyStatus, Clock clock, SkodaProperties properties) {
        return new SnapshotCache(publicApiClient, vehicleSnapshotMapper, apiKeyStatus, clock,
                properties.cache().ttl());
    }

    @Bean
    VehicleService vehicleService(SnapshotCache snapshotCache, ApiKeyStatus apiKeyStatus, Clock clock) {
        return new VehicleService(snapshotCache, apiKeyStatus, clock);
    }
}
