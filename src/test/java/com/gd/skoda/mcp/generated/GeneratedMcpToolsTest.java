package com.gd.skoda.mcp.generated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.gd.skoda.api.VehicleApiFacade;
import com.gd.skoda.api.generated.model.ApiKeyInfoDto;
import com.gd.skoda.api.generated.model.VehicleLocationResponseDto;
import com.gd.skoda.api.generated.model.VehicleOverviewResponseDto;
import com.gd.skoda.api.generated.model.VehicleRangeResponseDto;
import com.gd.skoda.api.generated.model.VehicleStatusResponseDto;
import com.gd.skoda.error.SkodaApiProblems;
import com.gd.skoda.mcp.McpToolErrors;
import com.gd.skoda.mcp.McpToolException;
import com.gd.skoda.upstream.ApiKeyExpiredException;
import com.gd.skoda.upstream.RateLimitExceededException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.http.HttpStatus;

/** The generated tool methods delegate to the facade and report failures through {@link McpToolErrors}. */
@DisabledInNativeImage // Mockito cannot create mocks in a native image
class GeneratedMcpToolsTest {

    private final VehicleApiFacade facade = mock(VehicleApiFacade.class);
    private final McpToolErrors errors = new McpToolErrors(new SkodaApiProblems("key-1234"));
    private final VehicleApiMcpTools vehicleTools = new VehicleApiMcpTools(facade, errors);
    private final SystemApiMcpTools systemTools = new SystemApiMcpTools(facade, errors);

    @Test
    void delegatesEveryToolToTheFacadeOperationOfTheSameName() {
        var overview = new VehicleOverviewResponseDto();
        var location = new VehicleLocationResponseDto();
        var range = new VehicleRangeResponseDto();
        var status = new VehicleStatusResponseDto();
        var apiKey = new ApiKeyInfoDto().rateLimitRemaining(17);
        given(facade.getVehicleOverview()).willReturn(overview);
        given(facade.getVehicleLocation()).willReturn(location);
        given(facade.getVehicleRange()).willReturn(range);
        given(facade.getVehicleStatus()).willReturn(status);
        given(facade.getApiKeyStatus()).willReturn(apiKey);

        assertThat(vehicleTools.getVehicleOverview()).isSameAs(overview);
        assertThat(vehicleTools.getVehicleLocation()).isSameAs(location);
        assertThat(vehicleTools.getVehicleRange()).isSameAs(range);
        assertThat(vehicleTools.getVehicleStatus()).isSameAs(status);
        assertThat(systemTools.getApiKeyStatus()).isSameAs(apiKey);
    }

    @Test
    void translatesUpstreamFailuresIntoToolErrors() {
        given(facade.getVehicleLocation()).willThrow(new RateLimitExceededException(HttpStatus.TOO_MANY_REQUESTS,
                null, Duration.ofSeconds(30)));

        assertThatThrownBy(vehicleTools::getVehicleLocation)
                .isInstanceOf(McpToolException.class)
                .hasMessage("get_vehicle_location failed: MySkoda rate limit exceeded.")
                .hasRootCauseMessage("Too many requests to the MySkoda Public API (the rate limit is per vehicle "
                        + "and hour). Try again in 30 seconds. This is temporary: do not call the tool again for 30 "
                        + "seconds. [problem: urn:skoda-mcp-server:problem:rate-limit-exceeded, "
                        + "retryAfterSeconds: 30, upstreamStatus: 429]");
    }

    @Test
    void namesTheFailingTool() {
        given(facade.getVehicleOverview()).willThrow(new ApiKeyExpiredException(HttpStatus.UNAUTHORIZED, null));
        given(facade.getApiKeyStatus()).willThrow(new IllegalStateException("boom"));

        assertThatThrownBy(vehicleTools::getVehicleOverview)
                .hasMessage("get_vehicle_overview failed: MySkoda API key expired.");
        assertThatThrownBy(systemTools::getApiKeyStatus)
                .hasMessage("get_api_key_status failed: Internal server error.");
    }
}
