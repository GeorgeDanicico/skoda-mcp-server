package com.gd.skoda.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns MyŠkoda Public API error responses into {@link SkodaApiException}s.
 *
 * <p>The body is parsed as RFC 9457 {@code application/problem+json} into Spring's
 * {@link ProblemDetail} via the JSON tree (no reflection, so nothing to register for native images).
 * A missing or unreadable body still maps by status code.
 */
public final class PublicApiErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    /** Problem type of a 401 caused by an expired key. */
    public static final URI API_KEY_EXPIRED =
            URI.create("https://public.api.connect.skoda-auto.cz/problems/api-key-expired");

    /** Upper bound for reading an error body; problem documents are tiny. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PublicApiErrorHandler(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        ProblemDetail problem = readProblem(response, status);
        throw toException(status, problem, response.getHeaders());
    }

    private SkodaApiException toException(HttpStatusCode status, @Nullable ProblemDetail problem,
            HttpHeaders headers) {
        @Nullable Duration retryAfter = UpstreamHeaders.retryAfter(headers, clock.instant()).orElse(null);
        return switch (status.value()) {
            case 401 -> problem != null && API_KEY_EXPIRED.equals(problem.getType())
                    ? new ApiKeyExpiredException(status, problem)
                    : new ApiKeyInvalidException(status, problem);
            case 403 -> new ApiKeyNotAuthorizedException(status, problem);
            case 404 -> new VehicleNotFoundException(status, problem);
            case 429 -> new RateLimitExceededException(status, problem, retryAfter);
            default -> status.is5xxServerError()
                    ? UpstreamUnavailableException.forStatus(status, problem, retryAfter)
                    : new UnexpectedUpstreamResponseException(status, problem);
        };
    }

    private @Nullable ProblemDetail readProblem(ClientHttpResponse response, HttpStatusCode status) {
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !isJson(contentType)) {
            return null;
        }
        try (InputStream body = response.getBody()) {
            JsonNode json = jsonMapper.readTree(body.readNBytes(MAX_BODY_BYTES));
            return json != null && json.isObject() ? toProblemDetail(json, status) : null;
        } catch (IOException | JacksonException ex) {
            return null;
        }
    }

    private static boolean isJson(MediaType contentType) {
        return MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)
                || MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
    }

    private static ProblemDetail toProblemDetail(JsonNode json, HttpStatusCode status) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        text(json, "type").flatMap(PublicApiErrorHandler::uri).ifPresent(problem::setType);
        text(json, "title").ifPresent(problem::setTitle);
        text(json, "detail").ifPresent(problem::setDetail);
        text(json, "instance").flatMap(PublicApiErrorHandler::uri).ifPresent(problem::setInstance);
        return problem;
    }

    private static Optional<String> text(JsonNode json, String field) {
        return json.optional(field).flatMap(JsonNode::stringValueOpt).filter(value -> !value.isBlank());
    }

    private static Optional<URI> uri(String value) {
        try {
            return Optional.of(URI.create(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
