package com.gd.skoda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.vehicle.VehicleData;
import com.gd.skoda.vehicle.VehicleService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

@DisabledInNativeImage // Mockito cannot create mocks in a native image
class VehicleApiFacadeTest {

    private final VehicleService vehicleService = mock(VehicleService.class);
    private final ServiceApiMapper mapper = new ServiceApiMapper();
    private final VehicleApiFacade facade = new VehicleApiFacade(vehicleService, mapper);

    @Test
    void overviewIsMappedFromService() {
        when(vehicleService.getOverview()).thenReturn(ApiTestData.fullOverview());

        assertThat(facade.getVehicleOverview()).isEqualTo(mapper.toOverviewResponse(ApiTestData.fullOverview()));
    }

    @Test
    void locationIsMappedFromService() {
        var data = new VehicleData<>(ApiTestData.LOCATION, ApiTestData.freshness(true));
        when(vehicleService.getLocation()).thenReturn(data);

        assertThat(facade.getVehicleLocation()).isEqualTo(mapper.toLocationResponse(data));
        assertThat(facade.getVehicleLocation().getFreshness().getStale()).isTrue();
    }

    @Test
    void rangeIsMappedFromService() {
        var data = new VehicleData<>(ApiTestData.RANGE, ApiTestData.freshness(false));
        when(vehicleService.getRange()).thenReturn(data);

        assertThat(facade.getVehicleRange()).isEqualTo(mapper.toRangeResponse(data));
    }

    @Test
    void statusIsMappedFromService() {
        var data = new VehicleData<>(ApiTestData.STATUS, ApiTestData.freshness(false));
        when(vehicleService.getStatus()).thenReturn(data);

        assertThat(facade.getVehicleStatus()).isEqualTo(mapper.toStatusResponse(data));
    }

    @Test
    void apiKeyStatusIsMappedFromServiceWithoutVehicleCall() {
        when(vehicleService.getApiKeyInfo()).thenReturn(ApiTestData.apiKeyInfo());

        assertThat(facade.getApiKeyStatus()).isEqualTo(mapper.toDto(ApiTestData.apiKeyInfo()));
        verify(vehicleService).getApiKeyInfo();
        verifyNoMoreInteractions(vehicleService);
    }

    @Test
    void propagatesServiceExceptions() {
        RateLimitExceededException failure = RateLimitExceededException.quotaExhausted(Duration.ofSeconds(60));
        when(vehicleService.getOverview()).thenThrow(failure);

        assertThatThrownBy(facade::getVehicleOverview).isSameAs(failure);
    }
}
