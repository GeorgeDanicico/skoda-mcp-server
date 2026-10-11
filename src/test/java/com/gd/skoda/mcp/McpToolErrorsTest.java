package com.gd.skoda.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.gd.skoda.error.ProblemTypes;
import com.gd.skoda.error.SkodaApiProblems;
import com.gd.skoda.upstream.ApiKeyInvalidException;
import com.gd.skoda.upstream.ApiKeyNotAuthorizedException;
import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.upstream.SkodaApiException;
import com.gd.skoda.upstream.UnexpectedUpstreamResponseException;
import com.gd.skoda.upstream.UpstreamUnavailableException;
import com.gd.skoda.upstream.VehicleNotFoundException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class McpToolErrorsTest {

    private static final String API_KEY = "my-secret-api-key";

    private final McpToolErrors errors = new McpToolErrors(new SkodaApiProblems(API_KEY));

    @Test
    void returnsResultOfSuccessfulOperation() {
        assertThat(errors.call("tool", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void runsOperationsWithoutResult() {
        AtomicBoolean ran = new AtomicBoolean();
        errors.run("tool", () -> ran.set(true));
        assertThat(ran).isTrue();
    }

    @Test
    void rateLimitTellsWhenToRetry() {
        McpToolException error = fail(new RateLimitExceededException(HttpStatus.TOO_MANY_REQUESTS, null,
                Duration.ofSeconds(90)));

        assertThat(error).hasMessage("get_vehicle_status failed: MySkoda rate limit exceeded.");
        assertThat(error.details()).isEqualTo("Too many requests to the MySkoda Public API (the rate limit is per "
                + "vehicle and hour). Try again in 90 seconds. This is temporary: do not call the tool again for 90 "
                + "seconds. [problem: urn:skoda-mcp-server:problem:rate-limit-exceeded, retryAfterSeconds: 90, "
                + "upstreamStatus: 429]");
        assertThat(error.problem().getType()).isEqualTo(ProblemTypes.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void localQuotaGuardHasNoUpstreamStatus() {
        McpToolException error = fail(RateLimitExceededException.quotaExhausted(Duration.ofMinutes(10)));

        assertThat(error.details())
                .contains("retryAfterSeconds: 600")
                .doesNotContain("upstreamStatus");
    }

    @Test
    void outageWithoutRetryAfterIsTemporary() {
        McpToolException error = fail(UpstreamUnavailableException.forStatus(HttpStatus.SERVICE_UNAVAILABLE, null,
                null));

        assertThat(error).hasMessage("get_vehicle_status failed: MySkoda unavailable.");
        assertThat(error.details())
                .contains("This is temporary: try again later.", "upstreamStatus: 503")
                .doesNotContain("retryAfterSeconds");
    }

    @Test
    void timeoutIsTemporary() {
        McpToolException error = fail(UpstreamUnavailableException.forIoError(new HttpTimeoutException("timed out")));

        assertThat(error).hasMessage("get_vehicle_status failed: MySkoda did not answer in time.");
        assertThat(error.details()).contains("This is temporary", ProblemTypes.UPSTREAM_TIMEOUT.toString());
    }

    @Test
    void configurationProblemsAreNotRetryable() {
        assertThat(fail(new ApiKeyNotAuthorizedException(HttpStatus.FORBIDDEN, null)).details())
                .contains("includes the vehicle set as SKODA_VIN", "Retrying will not help");
        assertThat(fail(new VehicleNotFoundException(HttpStatus.NOT_FOUND, null)).details())
                .contains("Check SKODA_VIN", "Retrying will not help");
        assertThat(fail(new UnexpectedUpstreamResponseException(HttpStatus.I_AM_A_TEAPOT, null)).details())
                .contains("Retrying will not help", "upstreamStatus: 418");
    }

    @Test
    void neverRevealsTheApiKey() {
        ProblemDetail upstream = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "API key " + API_KEY + " is unknown");

        McpToolException error = fail(new ApiKeyInvalidException(HttpStatus.UNAUTHORIZED, upstream));

        assertThat(error.getMessage() + error.details()).doesNotContain(API_KEY).contains("API key **** is unknown");
    }

    @Test
    void hidesDetailsOfUnexpectedErrors() {
        assertThatThrownBy(() -> errors.call("get_vehicle_range", () -> {
            throw new IllegalStateException("NPE in com.gd.skoda.Secret");
        }))
                .asInstanceOf(type(McpToolException.class))
                .satisfies(error -> {
                    assertThat(error).hasMessage("get_vehicle_range failed: Internal server error.");
                    assertThat(error.details()).isEqualTo("An unexpected error occurred in the Skoda MCP server. "
                            + "Details are in the server log. Retrying is unlikely to help. "
                            + "[problem: urn:skoda-mcp-server:problem:internal-error]");
                    assertThat(error.problem().getStatus()).isEqualTo(500);
                });
    }

    @Test
    void passesToolErrorsThrough() {
        McpToolException original = fail(new VehicleNotFoundException(HttpStatus.NOT_FOUND, null));

        assertThatThrownBy(() -> errors.call("other", () -> {
            throw original;
        })).isSameAs(original);
    }

    /** Spring AI renders "message + line break + root cause message"; both parts must be meaningful. */
    @Test
    void rootCauseCarriesTheDetails() {
        McpToolException error = fail(new VehicleNotFoundException(HttpStatus.NOT_FOUND, null));

        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isNotSameAs(error).hasMessage(error.details());
        assertThat(error.getStackTrace()).isEmpty();
    }

    private McpToolException fail(SkodaApiException ex) {
        try {
            errors.call("get_vehicle_status", () -> {
                throw ex;
            });
        } catch (McpToolException toolError) {
            return toolError;
        }
        throw new AssertionError("expected a McpToolException");
    }
}
