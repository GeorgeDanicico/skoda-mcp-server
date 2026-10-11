package com.gd.skoda.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gd.skoda.config.ServiceApiConfig;
import com.gd.skoda.config.SkodaPropertiesConfiguration;
import com.gd.skoda.upstream.ApiKeyExpiredException;
import com.gd.skoda.upstream.ApiKeyInvalidException;
import com.gd.skoda.upstream.ApiKeyNotAuthorizedException;
import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.upstream.UnexpectedUpstreamResponseException;
import com.gd.skoda.upstream.UpstreamUnavailableException;
import com.gd.skoda.upstream.VehicleNotFoundException;
import com.gd.skoda.vehicle.VehicleService;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Every {@code SkodaApiException} (and anything else) becomes the documented problem response. */
@WebMvcTest(properties = {"SKODA_VIN=TMBJJ7NE8L0123456", "SKODA_API_KEY=" + ApiExceptionHandlerWebMvcTest.API_KEY})
@Import({SkodaPropertiesConfiguration.class, ServiceApiConfig.class})
@ActiveProfiles("api")
@DisabledInAotMode("@MockitoBean: Mockito cannot create mocks in a native image")
class ApiExceptionHandlerWebMvcTest {

    static final String API_KEY = "upstream-api-key-123";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VehicleService vehicleService;

    private ResultActions overviewFailingWith(RuntimeException failure) throws Exception {
        when(vehicleService.getOverview()).thenThrow(failure);
        return mvc.perform(get("/api/v1/vehicle"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static ProblemDetail upstreamProblem(int status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("https://public.api.connect.skoda-auto.cz/problems/x"));
        return problem;
    }

    @Test
    void apiKeyExpiredIs503WithoutRetryAfter() throws Exception {
        overviewFailingWith(new ApiKeyExpiredException(HttpStatus.UNAUTHORIZED, upstreamProblem(401, "Key expired")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:api-key-expired"))
                .andExpect(jsonPath("$.title").value("MySkoda API key expired"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(containsString("set it as SKODA_API_KEY")))
                .andExpect(jsonPath("$.detail").value(containsString("(MySkoda: Key expired)")))
                .andExpect(jsonPath("$.instance").value("/api/v1/vehicle"))
                .andExpect(jsonPath("$.upstreamStatus").value(401))
                .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist());
    }

    @Test
    void apiKeyInvalidIs503AndNeverLeaksTheKey() throws Exception {
        overviewFailingWith(new ApiKeyInvalidException(HttpStatus.UNAUTHORIZED,
                upstreamProblem(401, "Unknown key " + API_KEY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:api-key-invalid"))
                .andExpect(jsonPath("$.detail").value(containsString("Unknown key ****")))
                .andExpect(content().string(not(containsString(API_KEY))));
    }

    @Test
    void apiKeyNotAuthorizedIs503() throws Exception {
        overviewFailingWith(new ApiKeyNotAuthorizedException(HttpStatus.FORBIDDEN, null))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:api-key-not-authorized"))
                .andExpect(jsonPath("$.upstreamStatus").value(403));
    }

    @Test
    void upstreamRateLimitIs429WithRetryAfter() throws Exception {
        overviewFailingWith(new RateLimitExceededException(HttpStatus.TOO_MANY_REQUESTS, null, Duration.ofSeconds(120)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "120"))
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:rate-limit-exceeded"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(120))
                .andExpect(jsonPath("$.upstreamStatus").value(429));
    }

    @Test
    void localQuotaGuardIs429WithRetryAfterButNoUpstreamStatus() throws Exception {
        overviewFailingWith(RateLimitExceededException.quotaExhausted(Duration.ofSeconds(90)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(90))
                .andExpect(jsonPath("$.upstreamStatus").doesNotExist())
                .andExpect(jsonPath("$.detail").value(containsString("almost used up")));
    }

    @Test
    void fractionalRetryAfterIsRoundedUp() throws Exception {
        overviewFailingWith(RateLimitExceededException.quotaExhausted(Duration.ofMillis(1500)))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"));
    }

    @Test
    void rateLimitWithoutKnownDelayHasNoRetryAfter() throws Exception {
        overviewFailingWith(RateLimitExceededException.quotaExhausted(null))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist());
    }

    @Test
    void vehicleNotFoundIs502() throws Exception {
        overviewFailingWith(new VehicleNotFoundException(HttpStatus.NOT_FOUND, null))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:vehicle-not-found"))
                .andExpect(jsonPath("$.detail").value(containsString("SKODA_VIN")));
    }

    @Test
    void upstream5xxIs503WithRetryAfter() throws Exception {
        overviewFailingWith(UpstreamUnavailableException.forStatus(HttpStatus.SERVICE_UNAVAILABLE, null,
                Duration.ofSeconds(30)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:upstream-unavailable"))
                .andExpect(jsonPath("$.upstreamStatus").value(503));
    }

    @Test
    void upstreamConnectionFailureIs503() throws Exception {
        overviewFailingWith(UpstreamUnavailableException.forIoError(new ConnectException("Connection refused")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.upstreamStatus").doesNotExist());
    }

    @Test
    void upstreamTimeoutIs504() throws Exception {
        overviewFailingWith(UpstreamUnavailableException.forIoError(
                new IOException("wrapped", new HttpTimeoutException("request timed out"))))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:upstream-timeout"));
    }

    @Test
    void upstream504Is504() throws Exception {
        overviewFailingWith(UpstreamUnavailableException.forStatus(HttpStatus.GATEWAY_TIMEOUT, null, null))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.upstreamStatus").value(504));
    }

    @Test
    void unexpectedUpstreamResponseIs502() throws Exception {
        overviewFailingWith(new UnexpectedUpstreamResponseException(HttpStatus.BAD_REQUEST, null))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:upstream-unexpected-response"))
                .andExpect(jsonPath("$.upstreamStatus").value(400));
    }

    @Test
    void anyOtherExceptionIs500WithoutInternals() throws Exception {
        overviewFailingWith(new IllegalStateException("internal secret detail"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:skoda-mcp-server:problem:internal-error"))
                .andExpect(content().string(not(containsString("internal secret detail"))));
    }

    @Test
    void springMvcErrorsKeepTheirStandardProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/vehicle"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }
}
