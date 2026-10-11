package com.gd.skoda.upstream;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * A call to the MyŠkoda Public API failed. Messages are meant for end users: they say what went
 * wrong and what to do about it.
 *
 * <p>The hierarchy is sealed so that callers (e.g. the REST error advice) can switch over it
 * exhaustively.
 */
public abstract sealed class SkodaApiException extends RuntimeException
        permits ApiKeyExpiredException, ApiKeyInvalidException, ApiKeyNotAuthorizedException,
        RateLimitExceededException, VehicleNotFoundException, UpstreamUnavailableException,
        UnexpectedUpstreamResponseException {

    private final @Nullable HttpStatusCode upstreamStatus;
    private final @Nullable ProblemDetail problem;

    protected SkodaApiException(String message, @Nullable HttpStatusCode upstreamStatus,
            @Nullable ProblemDetail problem, @Nullable Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.problem = problem;
    }

    /** HTTP status returned by the upstream API; empty when no response was received (I/O error). */
    public Optional<HttpStatusCode> upstreamStatus() {
        return Optional.ofNullable(upstreamStatus);
    }

    /** RFC 9457 problem returned by the upstream API, if the error response carried one. */
    public Optional<ProblemDetail> problem() {
        return Optional.ofNullable(problem);
    }

    /** Appends the upstream problem's {@code detail} (if any) to a user-facing message. */
    static String withUpstreamDetail(String message, @Nullable ProblemDetail problem) {
        String detail = problem != null ? problem.getDetail() : null;
        return (detail == null || detail.isBlank()) ? message : message + " (MySkoda: " + detail + ")";
    }

    /** "Try again in N seconds." or "Try again later." */
    static String tryAgain(@Nullable Duration retryAfter) {
        return retryAfter != null
                ? "Try again in " + retryAfter.toSeconds() + " seconds."
                : "Try again later.";
    }
}
