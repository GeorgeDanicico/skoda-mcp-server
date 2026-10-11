package com.gd.skoda.upstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gd.skoda.support.MutableClock;
import com.gd.skoda.upstream.publicapi.model.OverallVehicleStatus;
import com.gd.skoda.upstream.publicapi.model.Vehicle;
import com.gd.skoda.upstream.publicapi.model.VehicleError;
import com.gd.skoda.upstream.publicapi.model.VehiclePart;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** The generated client, interceptor and error mapping against a mocked MyŠkoda Public API. */
@ExtendWith(OutputCaptureExtension.class)
class PublicApiClientTest {

    private static final String VIN = "TMBJJ7NE8L0123456";
    private static final String API_KEY = "test-api-key-5f3a";
    private static final String BASE_URL = "https://public.api.test";
    private static final String VEHICLE_URL = BASE_URL + "/api/v1/vehicles/" + VIN;
    private static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final ApiKeyStatus apiKeyStatus = new ApiKeyStatus(clock);

    private MockRestServiceServer server;
    private PublicApiClient client;

    @BeforeEach
    void setUp() {
        ApiKeyInterceptor interceptor = new ApiKeyInterceptor(API_KEY, apiKeyStatus, new ApiKeyExpiryWarning(clock));
        PublicApiErrorHandler errorHandler = new PublicApiErrorHandler(JsonMapper.builder().build(), clock);
        RestClient.Builder builder =
                PublicApiHttpClients.configure(RestClient.builder(), URI.create(BASE_URL), interceptor, errorHandler);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PublicApiClient(PublicApiHttpClients.vehiclesApi(builder.build()), VIN);
    }

    @Test
    void fetchesAndParsesFullVehicle() throws IOException {
        server.expect(requestTo(VEHICLE_URL + "?include=info,status,fuelStatus,odometer,parkingPosition,charging"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(UpstreamHeaders.API_KEY, API_KEY))
                .andRespond(withSuccess(fixture("vehicle-full.json"), MediaType.APPLICATION_JSON));

        // unordered input; the query string follows the enum's declaration order
        VehicleResponse response = client.fetchVehicle(Set.of(VehiclePart.CHARGING, VehiclePart.PARKING_POSITION,
                VehiclePart.INFO, VehiclePart.ODOMETER, VehiclePart.FUEL_STATUS, VehiclePart.STATUS));

        server.verify();
        assertThat(response.getErrors()).isEmpty();
        Vehicle vehicle = response.getVehicle();
        assertThat(vehicle.getVin()).isEqualTo(VIN);
        assertThat(vehicle.getName()).isEqualTo("Family Octavia");
        assertThat(vehicle.getLicensePlate()).isEqualTo("B 123 XYZ");
        assertThat(vehicle.getRenderUrl()).hasHost("ip-modcwp.azureedge.net");

        OverallVehicleStatus overall = vehicle.getStatus().getOverall();
        assertThat(overall.getLocked()).isEqualTo("YES");
        assertThat(overall.getDoorsLocked()).isEqualTo("YES");
        assertThat(overall.getDoors()).isEqualTo("CLOSED");
        assertThat(overall.getWindows()).isEqualTo("CLOSED");
        assertThat(overall.getLights()).isEqualTo("OFF");
        assertThat(overall.getReliableLockStatus()).isEqualTo("LOCKED");
        assertThat(vehicle.getStatus().getDetail().getSunroof()).isEqualTo("UNSUPPORTED");
        assertThat(vehicle.getStatus().getDetail().getTrunk()).isEqualTo("CLOSED");
        assertThat(vehicle.getStatus().getCarCapturedTimestamp().toInstant())
                .isEqualTo(Instant.parse("2026-10-11T07:58:12Z"));

        assertThat(vehicle.getFuelStatus().getCarType()).isEqualTo("GASOLINE");
        assertThat(vehicle.getFuelStatus().getTotalRangeInKm()).isEqualByComparingTo("612");
        assertThat(vehicle.getFuelStatus().getAdBlueRange()).isNull();
        assertThat(vehicle.getFuelStatus().getPrimaryEngineRange().getEngineType()).isEqualTo("GASOLINE");
        assertThat(vehicle.getFuelStatus().getPrimaryEngineRange().getCurrentFuelLevelInPercent())
                .isEqualByComparingTo(BigDecimal.valueOf(74));
        assertThat(vehicle.getFuelStatus().getSecondaryEngineRange()).isNull();

        assertThat(vehicle.getOdometer().getMileageInKm()).isEqualTo(48213L);

        assertThat(vehicle.getParkingPosition().getState()).isEqualTo("PARKED");
        assertThat(vehicle.getParkingPosition().getGpsCoordinates().getLatitude()).isEqualTo(44.4361414);
        assertThat(vehicle.getParkingPosition().getGpsCoordinates().getLongitude()).isEqualTo(26.1027202);
        assertThat(vehicle.getParkingPosition().getFormattedAddress()).startsWith("Strada Lipscani 12");

        assertThat(vehicle.getAirConditioning().getTargetTemperature().getValue()).isEqualTo(21.5);
        assertThat(vehicle.getCharging()).isNull();
        assertThat(vehicle.getOperations()).extracting("name")
                .containsExactly("startAirConditioning", "stopAirConditioning");
    }

    @Test
    void omitsIncludeParameterWhenNoPartsAreRequested() throws IOException {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(withSuccess(fixture("vehicle-full.json"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchVehicle(Set.of()).getVehicle().getVin()).isEqualTo(VIN);
        server.verify();
    }

    @Test
    void sendsSinglePartWithItsWireName() throws IOException {
        server.expect(requestTo(VEHICLE_URL + "?include=fuelStatus"))
                .andRespond(withSuccess(fixture("vehicle-full.json"), MediaType.APPLICATION_JSON));

        client.fetchVehicle(EnumSet.of(VehiclePart.FUEL_STATUS));
        server.verify();
    }

    @Test
    void recordsKeyExpiryAndRateLimitFromResponseHeaders() throws IOException {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(withSuccess(fixture("vehicle-full.json"), MediaType.APPLICATION_JSON)
                        .headers(headers(
                                "X-API-Key-Expires-At", "2027-01-31T00:00:00Z",
                                "RateLimit-Limit", "20",
                                "RateLimit-Remaining", "17",
                                "RateLimit-Reset", "1800")));

        client.fetchVehicle(Set.of());

        ApiKeyStatus.Snapshot status = apiKeyStatus.current();
        assertThat(status.keyExpiresAt()).isEqualTo(Instant.parse("2027-01-31T00:00:00Z"));
        assertThat(status.rateLimitLimit()).isEqualTo(20);
        assertThat(status.rateLimitRemaining()).isEqualTo(17);
        assertThat(status.rateLimitResetAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(status.retryNotBefore()).isNull();
        assertThat(status.lastCallAt()).isEqualTo(NOW);
    }

    @Test
    void warnsAboutExpiringKeyWithoutLoggingTheKey(CapturedOutput output) throws IOException {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(withSuccess(fixture("vehicle-full.json"), MediaType.APPLICATION_JSON)
                        .headers(headers("X-API-Key-Expires-At", "2026-10-13T08:00:00Z")));

        client.fetchVehicle(Set.of());

        assertThat(output).contains("The MySkoda API key expires at 2026-10-13T08:00:00Z")
                .doesNotContain(API_KEY);
    }

    @Test
    void returnsPartialDataWithErrors() throws IOException {
        server.expect(requestTo(VEHICLE_URL + "?include=info,status,fuelStatus,odometer,parkingPosition,charging"))
                .andRespond(withSuccess(fixture("vehicle-partial.json"), MediaType.APPLICATION_JSON));

        VehicleResponse response = client.fetchVehicle(EnumSet.of(VehiclePart.INFO, VehiclePart.STATUS,
                VehiclePart.FUEL_STATUS, VehiclePart.ODOMETER, VehiclePart.PARKING_POSITION, VehiclePart.CHARGING));

        assertThat(response.getErrors()).extracting(VehicleError::getType).containsExactly(
                "RENDER_UNAVAILABLE", "PARKING_POSITION_UNAVAILABLE", "FUEL_STATUS_DISABLED", "CHARGING_UNSUPPORTED");
        assertThat(response.getErrors().get(1).getDescription()).contains("Parking position");
        assertThat(response.getErrors().get(2).getDescription()).isNull();

        Vehicle vehicle = response.getVehicle();
        assertThat(vehicle.getRenderUrl()).isNull();
        assertThat(vehicle.getParkingPosition()).isNull();
        assertThat(vehicle.getFuelStatus()).isNull();
        assertThat(vehicle.getCharging()).isNull();
        assertThat(vehicle.getOperations()).isEmpty();
        assertThat(vehicle.getStatus().getOverall().getWindows()).isEqualTo("OPEN");
        assertThat(vehicle.getStatus().getOverall().getReliableLockStatus()).isNull();
        // offset timestamps are preserved as the same instant; unknown fields are ignored
        assertThat(vehicle.getStatus().getCarCapturedTimestamp().toInstant())
                .isEqualTo(Instant.parse("2026-10-11T05:58:12Z"));
        assertThat(vehicle.getOdometer().getMileageInKm()).isEqualTo(48213L);
        assertThat(vehicle.getOdometer().getCarCapturedTimestamp()).isNull();
    }

    @Test
    void mapsExpiredKeyTo401ApiKeyExpired() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.UNAUTHORIZED, """
                        {"type": "https://public.api.connect.skoda-auto.cz/problems/api-key-expired",
                         "title": "Unauthorized", "status": 401,
                         "detail": "The API key expired on 2026-10-01T00:00:00Z."}""")
                        .headers(headers("X-API-Key-Expires-At", "2026-10-01T00:00:00Z")));

        assertThatExceptionOfType(ApiKeyExpiredException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("Create a new API key in the MySkoda app and set it as SKODA_API_KEY")
                .withMessageContaining("The API key expired on 2026-10-01T00:00:00Z.")
                .satisfies(ex -> {
                    assertThat(ex.upstreamStatus()).contains(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.problem()).hasValueSatisfying(problem -> {
                        assertThat(problem.getType()).isEqualTo(PublicApiErrorHandler.API_KEY_EXPIRED);
                        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
                        assertThat(problem.getStatus()).isEqualTo(401);
                    });
                });
        assertThat(apiKeyStatus.current().keyExpiresAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void mapsOther401ToApiKeyInvalid() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.UNAUTHORIZED, """
                        {"type": "about:blank", "title": "Unauthorized", "status": 401}"""));

        assertThatExceptionOfType(ApiKeyInvalidException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("rejected the API key")
                .withMessageContaining("MySkoda app");
    }

    @Test
    void maps403ToApiKeyNotAuthorized() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.FORBIDDEN, """
                        {"type": "https://public.api.connect.skoda-auto.cz/problems/api-key-not-authorized",
                         "title": "Forbidden", "status": 403,
                         "detail": "The API key is not authorized to access vehicle TMBJJ7NE8L0123456."}"""));

        assertThatExceptionOfType(ApiKeyNotAuthorizedException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("In the MySkoda app, create or update the API key")
                .withMessageContaining("not authorized to access vehicle TMBJJ7NE8L0123456");
    }

    @Test
    void maps404ToVehicleNotFound() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.NOT_FOUND, """
                        {"type": "about:blank", "title": "Not Found", "status": 404,
                         "detail": "No vehicle found for VIN TMBJJ7NE8L0123456."}"""));

        assertThatExceptionOfType(VehicleNotFoundException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("Check SKODA_VIN");
    }

    @Test
    void maps429ToRateLimitExceededWithRetryAfter() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.TOO_MANY_REQUESTS, """
                        {"type": "https://public.api.connect.skoda-auto.cz/problems/rate-limit-exceeded",
                         "title": "Too Many Requests", "status": 429,
                         "detail": "Rate limit exceeded. Retry after 120 seconds."}""")
                        .headers(headers(
                                "Retry-After", "120",
                                "RateLimit-Limit", "20",
                                "RateLimit-Remaining", "0",
                                "RateLimit-Reset", "120")));

        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("Try again in 120 seconds.")
                .satisfies(ex -> assertThat(ex.retryAfter()).contains(Duration.ofSeconds(120)));
        assertThat(apiKeyStatus.current().rateLimitRemaining()).isZero();
        assertThat(apiKeyStatus.current().retryNotBefore()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void maps429WithoutRetryAfter() {
        server.expect(requestTo(VEHICLE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("Try again later.")
                .satisfies(ex -> {
                    assertThat(ex.retryAfter()).isEmpty();
                    assertThat(ex.problem()).isEmpty();
                });
    }

    @Test
    void maps503ToUpstreamUnavailable() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.SERVICE_UNAVAILABLE, """
                        {"type": "about:blank", "title": "Service Unavailable", "status": 503,
                         "detail": "API key validation is temporarily unavailable. Retry after 10 seconds."}""")
                        .headers(headers("Retry-After", "10")));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("temporarily unavailable (HTTP 503). Try again in 10 seconds.")
                .satisfies(ex -> {
                    assertThat(ex.retryAfter()).contains(Duration.ofSeconds(10));
                    assertThat(ex.upstreamStatus()).contains(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void maps5xxWithNonJsonBodyToUpstreamUnavailable() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                        .body("<html><body>Bad gateway</body></html>"));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("HTTP 502")
                .satisfies(ex -> assertThat(ex.problem()).isEmpty());
    }

    @Test
    void mapsMalformedProblemBodyByStatus() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.GATEWAY_TIMEOUT, "{ not json"));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("HTTP 504")
                .satisfies(ex -> assertThat(ex.problem()).isEmpty());
    }

    @Test
    void mapsOtherClientErrorsToUnexpectedResponse() {
        server.expect(requestTo(VEHICLE_URL))
                .andRespond(problem(HttpStatus.BAD_REQUEST, """
                        {"type": "about:blank", "title": "Bad Request", "status": 400,
                         "detail": "Invalid value 'nonsense' for parameter 'include'.",
                         "parameter": "include", "rejectedValue": "nonsense"}"""));

        assertThatExceptionOfType(UnexpectedUpstreamResponseException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("HTTP 400")
                .withMessageContaining("Invalid value 'nonsense' for parameter 'include'.");
    }

    @Test
    void mapsIoFailureToUpstreamUnavailable() {
        server.expect(requestTo(VEHICLE_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatExceptionOfType(UpstreamUnavailableException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("Could not reach the MySkoda Public API (Read timed out)")
                .withCauseInstanceOf(SocketTimeoutException.class)
                .satisfies(ex -> assertThat(ex.upstreamStatus()).isEmpty());
        assertThat(apiKeyStatus.current().lastCallAt()).isNull();
    }

    @Test
    void mapsUnreadableSuccessBodyToUnexpectedResponse() {
        server.expect(requestTo(VEHICLE_URL)).andRespond(withSuccess("{\"vehicle\": [", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(UnexpectedUpstreamResponseException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("could not be read");
    }

    @Test
    void rejectsResponseWithoutVehicle() {
        server.expect(requestTo(VEHICLE_URL)).andRespond(withSuccess("{\"errors\": []}", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(UnexpectedUpstreamResponseException.class)
                .isThrownBy(() -> client.fetchVehicle(Set.of()))
                .withMessageContaining("empty");
    }

    private static DefaultResponseCreator problem(HttpStatus status, String json) {
        return withStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(json);
    }

    private static HttpHeaders headers(String... namesAndValues) {
        HttpHeaders headers = new HttpHeaders();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            headers.add(namesAndValues[i], namesAndValues[i + 1]);
        }
        return headers;
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("upstream/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
