package com.gd.skoda.error;

import com.gd.skoda.upstream.ApiKeyExpiredException;
import com.gd.skoda.upstream.ApiKeyInvalidException;
import com.gd.skoda.upstream.ApiKeyNotAuthorizedException;
import com.gd.skoda.upstream.RateLimitExceededException;
import com.gd.skoda.upstream.SkodaApiException;
import com.gd.skoda.upstream.UnexpectedUpstreamResponseException;
import com.gd.skoda.upstream.UpstreamUnavailableException;
import com.gd.skoda.upstream.VehicleNotFoundException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Translates {@link SkodaApiException}s into RFC 9457 {@link ProblemDetail}s. Free of web types beyond
 * {@code ProblemDetail}, so it is usable from REST ({@link ApiExceptionHandler}) and MCP alike.
 *
 * <p>Status codes (our client is never at fault for an upstream failure, so no 4xx except 429):
 * <ul>
 *   <li>API key expired / invalid / not authorised → <b>503</b>: the service cannot serve vehicle data
 *       until the operator installs a new key. 502 would claim MyŠkoda misbehaved (it answered
 *       correctly), 401/403 would tell our client that <em>its</em> credentials are wrong.</li>
 *   <li>Rate limit (upstream 429 or the local quota guard) → <b>429</b> + {@code Retry-After} when known.</li>
 *   <li>Vehicle not found → <b>502</b>: MyŠkoda rejected the server's configured VIN. A 404 would say our
 *       resource does not exist (and 404 is heuristically cacheable).</li>
 *   <li>Upstream 5xx / I/O error → <b>503</b> (+ {@code Retry-After} when MyŠkoda sent one); upstream 504 or a
 *       timeout → <b>504</b>.</li>
 *   <li>Unexpected upstream response → <b>502</b>.</li>
 * </ul>
 *
 * <p>Details are the exceptions' user-facing messages, which never contain the API key; as a second
 * line of defence any occurrence of the key is masked anyway.
 */
public final class SkodaApiProblems {

    /** Problem extension member: seconds until a retry makes sense (mirrors {@code Retry-After}). */
    public static final String RETRY_AFTER_SECONDS = "retryAfterSeconds";
    /** Problem extension member: HTTP status MyŠkoda answered with, if it answered. */
    public static final String UPSTREAM_STATUS = "upstreamStatus";

    private static final int MIN_SECRET_LENGTH_TO_MASK = 4;

    private final @Nullable String apiKey;

    /** @param apiKey the MyŠkoda API key, masked wherever it might appear in a detail */
    public SkodaApiProblems(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    /** The problem for {@code ex}; {@link #RETRY_AFTER_SECONDS} is set when a retry delay is known. */
    public ProblemDetail toProblem(SkodaApiException ex) {
        ProblemDetail problem = switch (ex) {
            case ApiKeyExpiredException e ->
                    problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.API_KEY_EXPIRED, "MySkoda API key expired", e);
            case ApiKeyInvalidException e ->
                    problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.API_KEY_INVALID, "MySkoda API key rejected", e);
            case ApiKeyNotAuthorizedException e -> problem(HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemTypes.API_KEY_NOT_AUTHORIZED, "MySkoda API key not authorized for the vehicle", e);
            case RateLimitExceededException e -> withRetryAfter(problem(HttpStatus.TOO_MANY_REQUESTS,
                    ProblemTypes.RATE_LIMIT_EXCEEDED, "MySkoda rate limit exceeded", e), e.retryAfter());
            case VehicleNotFoundException e ->
                    problem(HttpStatus.BAD_GATEWAY, ProblemTypes.VEHICLE_NOT_FOUND, "Vehicle not found at MySkoda", e);
            case UpstreamUnavailableException e -> isTimeout(e)
                    ? problem(HttpStatus.GATEWAY_TIMEOUT, ProblemTypes.UPSTREAM_TIMEOUT,
                            "MySkoda did not answer in time", e)
                    : withRetryAfter(problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.UPSTREAM_UNAVAILABLE,
                            "MySkoda unavailable", e), e.retryAfter());
            case UnexpectedUpstreamResponseException e -> problem(HttpStatus.BAD_GATEWAY,
                    ProblemTypes.UPSTREAM_UNEXPECTED_RESPONSE, "Unexpected response from MySkoda", e);
        };
        ex.upstreamStatus().ifPresent(status -> problem.setProperty(UPSTREAM_STATUS, status.value()));
        return problem;
    }

    /** {@link #RETRY_AFTER_SECONDS} of a problem built by {@link #toProblem}, if present. */
    public static Optional<Long> retryAfterSeconds(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().get(RETRY_AFTER_SECONDS) instanceof Long s
                ? Optional.of(s)
                : Optional.empty();
    }

    /** Masks the API key in {@code text}. */
    public @Nullable String redact(@Nullable String text) {
        if (text == null || apiKey == null || apiKey.length() < MIN_SECRET_LENGTH_TO_MASK) {
            return text;
        }
        return text.replace(apiKey, "****");
    }

    private ProblemDetail problem(HttpStatus status, URI type, String title, SkodaApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, redact(ex.getMessage()));
        problem.setType(type);
        problem.setTitle(title);
        return problem;
    }

    private static ProblemDetail withRetryAfter(ProblemDetail problem, Optional<Duration> retryAfter) {
        retryAfter.filter(d -> !d.isNegative())
                .ifPresent(d -> problem.setProperty(RETRY_AFTER_SECONDS, ceilSeconds(d)));
        return problem;
    }

    private static long ceilSeconds(Duration duration) {
        return duration.getNano() == 0 ? duration.getSeconds() : duration.getSeconds() + 1;
    }

    private static boolean isTimeout(UpstreamUnavailableException ex) {
        if (ex.upstreamStatus().filter(s -> s.value() == HttpStatus.GATEWAY_TIMEOUT.value()).isPresent()) {
            return true;
        }
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException
                    || t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
