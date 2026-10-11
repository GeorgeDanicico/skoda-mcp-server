package com.gd.skoda;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.mcp.McpToolErrors;
import com.gd.skoda.mcp.generated.SystemApiMcpTools;
import com.gd.skoda.mcp.generated.VehicleApiMcpTools;
import io.modelcontextprotocol.server.McpSyncServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/** api profile: servlet app, no MCP server, health on the separate management port. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "SKODA_VIN=TMBJJ7NE8L0123456",
        "SKODA_API_KEY=test-key",
        "SKODA_PUBLIC_API_URL=",
        "SKODA_CACHE_TTL=",
        "management.server.port=0"
})
@ActiveProfiles("api")
class ApiProfileApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void runsAsServletApplicationWithoutMcpServer() {
        assertThat(context).isInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeansOfType(McpSyncServer.class)).isEmpty();
    }

    @Test
    void doesNotRegisterMcpTools() {
        assertThat(context.getBeansOfType(McpToolErrors.class)).isEmpty();
        assertThat(context.getBeansOfType(VehicleApiMcpTools.class)).isEmpty();
        assertThat(context.getBeansOfType(SystemApiMcpTools.class)).isEmpty();
    }

    @Test
    void exposesHealthOnManagementPortOnly() throws Exception {
        int appPort = environment.getRequiredProperty("local.server.port", Integer.class);
        int managementPort = environment.getRequiredProperty("local.management.port", Integer.class);
        assertThat(managementPort).isNotEqualTo(appPort);

        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(get(client, managementPort, "/actuator/health").statusCode()).isEqualTo(200);
            assertThat(get(client, appPort, "/actuator/health").statusCode()).isEqualTo(404);
        }
    }

    @Test
    void servesOurContractAndSwaggerUi() throws Exception {
        int appPort = environment.getRequiredProperty("local.server.port", Integer.class);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> spec = get(client, appPort, "/skoda-service-api.yaml");
            assertThat(spec.statusCode()).isEqualTo(200);
            assertThat(spec.headers().firstValue("Content-Type")).hasValueSatisfying(
                    type -> assertThat(type).startsWith("application/yaml"));
            assertThat(spec.body()).contains("operationId: getVehicleOverview", "x-mcp-name: get_vehicle_overview");

            HttpResponse<String> swaggerUi = get(client, appPort, "/swagger-ui/index.html");
            assertThat(swaggerUi.statusCode()).isEqualTo(200);
            assertThat(swaggerUi.body()).contains("swagger-ui");

            HttpResponse<String> swaggerConfig = get(client, appPort, "/swagger-ui/swagger-initializer.js");
            assertThat(swaggerConfig.statusCode()).isEqualTo(200);
            assertThat(swaggerConfig.body()).contains("/skoda-service-api.yaml");

            // springdoc's code-derived document is kept empty: the static file is the contract
            HttpResponse<String> derived = get(client, appPort, "/v3/api-docs");
            assertThat(derived.statusCode()).isEqualTo(200);
            assertThat(derived.body()).doesNotContain("/api/v1/vehicle");
        }
    }

    @Test
    void servesRestApiWithoutAuthentication() throws Exception {
        int appPort = environment.getRequiredProperty("local.server.port", Integer.class);

        try (HttpClient client = HttpClient.newHttpClient()) {
            // api-key status never calls upstream, so this stays offline
            HttpResponse<String> response = get(client, appPort, "/api/v1/system/api-key");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"keyExpiresAt\":null");
        }
    }
    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
