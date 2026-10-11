package com.gd.skoda.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.gd.skoda.api.ApiTestData;
import com.gd.skoda.upstream.ApiKeyExpiredException;
import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.vehicle.ApiKeyInfo;
import com.gd.skoda.vehicle.Location;
import com.gd.skoda.vehicle.Range;
import com.gd.skoda.vehicle.Status;
import com.gd.skoda.vehicle.VehicleData;
import com.gd.skoda.vehicle.VehicleService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Default (mcp) run mode: the generated tools are registered with the MCP server with the contract's
 * names, descriptions and schemas, and calls go through Spring AI's tool callback to the facade.
 * {@link VehicleService} is mocked, so the real facade and DTO mapping produce the results.
 */
@SpringBootTest(properties = {
        "SKODA_VIN=TMBJJ7NE8L0123456",
        "SKODA_API_KEY=secret-test-key",
        "SKODA_PUBLIC_API_URL=",
        "SKODA_CACHE_TTL="
})
@DisabledInAotMode("@MockitoBean: Mockito cannot create mocks in a native image")
class McpToolsRegistrationTest {

    private static final List<String> TOOL_NAMES = List.of("get_api_key_status", "get_vehicle_location",
            "get_vehicle_overview", "get_vehicle_range", "get_vehicle_status");

    private static final JsonSchemaValidator SCHEMA_VALIDATOR = McpJsonDefaults.getSchemaValidator();

    @MockitoBean
    private VehicleService vehicleService;

    @Autowired
    private McpSyncServer server;

    @Autowired
    @Qualifier("toolSpecs")
    private List<SyncToolSpecification> toolSpecs;

    @Test
    void registersOneToolPerContractOperation() {
        assertThat(server.listTools()).extracting(Tool::name).containsExactlyInAnyOrderElementsOf(TOOL_NAMES);
        assertThat(server.getServerCapabilities().tools()).isNotNull();
        assertThat(server.getServerCapabilities().resources()).isNull();
        assertThat(server.getServerCapabilities().prompts()).isNull();
        assertThat(server.getServerCapabilities().completions()).isNull();
    }

    @Test
    void describesToolsWithContractTexts() {
        Map<String, Tool> tools = tools();

        assertThat(tools.get("get_vehicle_location").description())
                .startsWith("Get where the vehicle is parked, or whether it is moving. Returns the last known")
                .contains("`state` is PARKED or IN_MOTION.")
                .doesNotContain("\n")
                .endsWith("disabled in the vehicle.");
        // quotes in the spec text survive the trip through a generated Java string literal
        assertThat(tools.get("get_vehicle_range").description())
                .contains("entries meaning \"not supported by this vehicle\" are omitted");
        assertThat(tools.get("get_vehicle_overview").description()).contains("MyŠkoda did not deliver");
    }

    @Test
    void marksAllToolsReadOnlyAndIdempotent() {
        assertThat(server.listTools()).allSatisfy(tool -> {
            assertThat(tool.title()).isEqualTo(tool.name());
            assertThat(tool.annotations().readOnlyHint()).isTrue();
            assertThat(tool.annotations().destructiveHint()).isFalse();
            assertThat(tool.annotations().idempotentHint()).isTrue();
            assertThat(tool.annotations().openWorldHint()).isTrue();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void takesNoArguments() {
        assertThat(server.listTools()).allSatisfy(tool -> {
            assertThat(tool.inputSchema()).containsEntry("type", "object");
            assertThat((Map<String, Object>) tool.inputSchema().get("properties")).isEmpty();
            assertThat((List<String>) tool.inputSchema().get("required")).isEmpty();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesResponseSchemaAsOutputSchema() {
        Map<String, Tool> tools = tools();
        for (String name : List.of("get_vehicle_location", "get_vehicle_overview", "get_vehicle_range",
                "get_vehicle_status")) {
            Map<String, Object> schema = tools.get(name).outputSchema();
            assertThat(schema).as(name).containsEntry("type", "object");
            assertThat((Map<String, Object>) schema.get("properties")).as(name).containsKeys("data", "freshness");
        }
        assertThat((Map<String, Object>) tools.get("get_api_key_status").outputSchema().get("properties"))
                .containsKeys("keyExpiresAt", "daysUntilExpiry", "rateLimitLimit", "rateLimitRemaining",
                        "rateLimitResetAt", "lastUpstreamCallAt");

        // field descriptions and nullability come from the contract (via @Schema on the generated DTOs)
        Map<String, Object> location = tools.get("get_vehicle_location").outputSchema();
        Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) location.get("properties")).get("data");
        Map<String, Object> latitude = (Map<String, Object>) ((Map<String, Object>) data.get("properties")).get("latitude");
        assertThat(latitude)
                .containsEntry("type", List.of("number", "null"))
                .containsEntry("description", "WGS84 latitude in decimal degrees (only while PARKED).");
        assertThat(location).containsEntry("required", List.of("data", "freshness"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsStructuredResults() {
        given(vehicleService.getLocation())
                .willReturn(new VehicleData<>(ApiTestData.LOCATION, ApiTestData.freshness(false)));

        CallToolResult result = call("get_vehicle_location");

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        Map<String, Object> content = (Map<String, Object>) result.structuredContent();
        assertThat((Map<String, Object>) content.get("data"))
                .containsEntry("state", "PARKED")
                .containsEntry("latitude", 50.4113)
                .containsEntry("longitude", 14.9083);
        assertThat((Map<String, Object>) content.get("freshness"))
                .containsEntry("fetchedAt", "2026-10-11T10:00:00Z")
                .containsEntry("stale", false);
    }

    /** The MCP server validates structured results against the output schema; nulls must pass. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void resultsConformToOutputSchemas(boolean complete) {
        if (complete) {
            given(vehicleService.getOverview()).willReturn(ApiTestData.fullOverview());
            given(vehicleService.getLocation()).willReturn(view(ApiTestData.LOCATION));
            given(vehicleService.getRange()).willReturn(view(ApiTestData.RANGE));
            given(vehicleService.getStatus()).willReturn(view(ApiTestData.STATUS));
            given(vehicleService.getApiKeyInfo()).willReturn(ApiTestData.apiKeyInfo());
        } else {
            given(vehicleService.getOverview()).willReturn(ApiTestData.emptyOverview(true));
            given(vehicleService.getLocation()).willReturn(view(Location.EMPTY));
            given(vehicleService.getRange()).willReturn(view(Range.EMPTY));
            given(vehicleService.getStatus()).willReturn(view(Status.EMPTY));
            given(vehicleService.getApiKeyInfo()).willReturn(new ApiKeyInfo(null, null, null, null, null, null));
        }
        Map<String, Tool> tools = tools();

        for (String name : TOOL_NAMES) {
            CallToolResult result = call(name);
            assertThat(result.isError()).as(name).isNotEqualTo(Boolean.TRUE);
            JsonSchemaValidator.ValidationResponse validation =
                    SCHEMA_VALIDATOR.validate(tools.get(name).outputSchema(), result.structuredContent());
            assertThat(validation.valid()).as(name + ": " + validation.errorMessage()).isTrue();
        }
    }

    @Test
    void reportsRateLimitAsActionableToolError() {
        ProblemDetail upstream = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Quota for key secret-test-key used up");
        given(vehicleService.getStatus()).willThrow(new RateLimitExceededException(HttpStatus.TOO_MANY_REQUESTS,
                upstream, Duration.ofSeconds(119).plusMillis(500)));

        CallToolResult result = call("get_vehicle_status");

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .startsWith("get_vehicle_status failed: MySkoda rate limit exceeded." + System.lineSeparator())
                .contains("Too many requests to the MySkoda Public API")
                .contains("do not call the tool again for 120 seconds")
                .contains("retryAfterSeconds: 120", "upstreamStatus: 429",
                        "problem: urn:skoda-mcp-server:problem:rate-limit-exceeded")
                .doesNotContain("secret-test-key");
    }

    @Test
    void reportsExpiredApiKeyAsNotRetryable() {
        given(vehicleService.getOverview())
                .willThrow(new ApiKeyExpiredException(HttpStatus.UNAUTHORIZED, null));

        CallToolResult result = call("get_vehicle_overview");

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .startsWith("get_vehicle_overview failed: MySkoda API key expired.")
                .contains("Create a new API key in the MySkoda app", "Retrying will not help");
    }

    @Test
    void hidesInternalErrors() {
        given(vehicleService.getRange()).willThrow(new IllegalStateException("bug at com.gd.skoda.Internal"));

        CallToolResult result = call("get_vehicle_range");

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .startsWith("get_vehicle_range failed: Internal server error.")
                .doesNotContain("com.gd.skoda.Internal");
    }

    private Map<String, Tool> tools() {
        return server.listTools().stream().collect(Collectors.toMap(Tool::name, Function.identity()));
    }

    private CallToolResult call(String toolName) {
        SyncToolSpecification spec = toolSpecs.stream()
                .filter(s -> s.tool().name().equals(toolName))
                .findFirst()
                .orElseThrow();
        // the annotated tools need no exchange (no McpSyncRequestContext parameters)
        return spec.callHandler().apply(null, new CallToolRequest(toolName, Map.of()));
    }

    private static String text(CallToolResult result) {
        assertThat(result.content()).hasSize(1).first().isInstanceOf(TextContent.class);
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    private static <T> VehicleData<T> view(T data) {
        return new VehicleData<>(data, ApiTestData.freshness(false, ApiTestData.CHARGING_UNSUPPORTED));
    }
}
