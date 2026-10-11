package com.gd.skoda.upstream;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** The upstream API is temporarily unavailable: a 5xx response, an I/O error or a timeout. */
public final class UpstreamUnavailableException extends SkodaApiException {

    private final @Nullable Duration retryAfter;

    private UpstreamUnavailableException(String message, @Nullable HttpStatusCode upstreamStatus,
            @Nullable ProblemDetail problem, @Nullable Duration retryAfter, @Nullable Throwable cause) {
        super(message, upstreamStatus, problem, cause);
        this.retryAfter = retryAfter;
    }

    /** The upstream API answered with a 5xx status. */
    public static UpstreamUnavailableException forStatus(HttpStatusCode upstreamStatus,
            @Nullable ProblemDetail problem, @Nullable Duration retryAfter) {
        String message = withUpstreamDetail("The MySkoda Public API is temporarily unavailable (HTTP "
                + upstreamStatus.value() + "). " + tryAgain(retryAfter), problem);
        return new UpstreamUnavailableException(message, upstreamStatus, problem, retryAfter, null);
    }

    /** No response was received (connection failure, timeout, ...). */
    public static UpstreamUnavailableException forIoError(Throwable cause) {
        String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        String message = "Could not reach the MySkoda Public API (" + reason + "). Check the network connection "
                + "and SKODA_PUBLIC_API_URL, then try again later.";
        return new UpstreamUnavailableException(message, null, null, null, cause);
    }

    /** How long to wait before the next request ({@code Retry-After}), if the API said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}