package com.gd.skoda;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.api.VehicleApiFacade;
import com.gd.skoda.api.generated.SystemApi;
import com.gd.skoda.api.generated.VehicleApi;
import com.gd.skoda.api.generated.model.ApiKeyInfoDto;
import com.gd.skoda.config.SkodaProperties;
import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.PublicApiClient;
import com.gd.skoda.vehicle.ApiKeyInfo;
import com.gd.skoda.vehicle.VehicleService;
import io.modelcontextprotocol.server.McpSyncServer;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/** Default (mcp) profile with configuration supplied through the documented env var names. */
@SpringBootTest(properties = {
        "SKODA_VIN=TMBJJ7NE8L0123456",
        "SKODA_API_KEY=test-key",
        "SKODA_PUBLIC_API_URL=",
        "SKODA_CACHE_TTL=2m"
})
class McpProfileApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SkodaProperties properties;

    /*
     * Asserts the effect of spring.profiles.default=mcp rather than Environment#getDefaultProfiles():
     * Boot's EnvironmentConverter drops default profiles when web-application-type=none switches the
     * environment type, so the final environment reports "default". Use @Profile("!api") for mcp-only beans.
     */
    @Test
    void appliesMcpProfileConfigurationByDefault() {
        assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(context.getEnvironment().getProperty("spring.ai.mcp.server.stdio", Boolean.class)).isTrue();
    }

    @Test
    void startsSyncMcpServerWithoutWebServer() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeansOfType(McpSyncServer.class)).hasSize(1);
        assertThat(context.getBean(McpSyncServer.class).getServerInfo().name()).isEqualTo("skoda-mcp");
    }

    @Test
    void wiresPublicApiClientForConfiguredVehicle() {
        assertThat(context.getBean(PublicApiClient.class).vin()).isEqualTo("TMBJJ7NE8L0123456");
        assertThat(context.getBean(ApiKeyStatus.class).current()).isEqualTo(ApiKeyStatus.Snapshot.EMPTY);
    }

    @Test
    void wiresVehicleServiceWithoutCallingUpstream() {
        assertThat(context.getBean(VehicleService.class).getApiKeyInfo())
                .isEqualTo(new ApiKeyInfo(null, null, null, null, null, null));
    }

    @Test
    void registersFacadeButNoRestLayer() {
        assertThat(context.getBeansOfType(VehicleApiFacade.class)).hasSize(1);
        assertThat(context.getBeansWithAnnotation(RestController.class)).isEmpty();
        assertThat(context.getBeansWithAnnotation(ControllerAdvice.class)).isEmpty();
        assertThat(context.getBeansOfType(FilterRegistrationBean.class)).isEmpty();
        assertThat(context.getBeanNamesForType(VehicleApi.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SystemApi.class)).isEmpty();
    }

    @Test
    void facadeServesApiKeyStatusWithoutCallingUpstream() {
        assertThat(context.getBean(VehicleApiFacade.class).getApiKeyStatus()).isEqualTo(new ApiKeyInfoDto());
    }

    @Test
    void mapsEnvironmentVariablesToProperties() {
        assertThat(properties.vin()).isEqualTo("TMBJJ7NE8L0123456");
        assertThat(properties.publicApi().key()).isEqualTo("test-key");
        assertThat(properties.publicApi().baseUrl()).isEqualTo(URI.create("https://public.api.connect.skoda-auto.cz"));
        assertThat(properties.publicApi().timeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.cache().ttl()).isEqualTo(Duration.ofMinutes(2));
    }
}
