package com.gd.skoda.upstream;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * 429: the rate limit of the API key is used up ({@code rate-limit-exceeded}) or the vehicle declined
 * the request ({@code vehicle-not-accepting-requests}). Retrying before {@link #retryAfter()} fails again.
 */
public final class RateLimitExceededException extends SkodaApiException {

    private final @Nullable Duration retryAfter;

    public RateLimitExceededException(HttpStatusCode upstreamStatus, @Nullable ProblemDetail problem,
            @Nullable Duration retryAfter) {
        this(withUpstreamDetail("Too many requests to the MySkoda Public API (the rate limit is per vehicle and "
                + "hour). " + tryAgain(retryAfter), problem), upstreamStatus, problem, retryAfter);
    }

    private RateLimitExceededException(String message, @Nullable HttpStatusCode upstreamStatus,
            @Nullable ProblemDetail problem, @Nullable Duration retryAfter) {
        super(message, upstreamStatus, problem, null);
        this.retryAfter = retryAfter;
    }

    /**
     * The request was not sent because the remaining quota is (almost) used up and no cached data
     * can be served instead. {@link #upstreamStatus()} is empty because no upstream call was made.
     *
     * @param retryAfter time until the quota window resets
     */
    public static RateLimitExceededException quotaExhausted(@Nullable Duration retryAfter) {
        return new RateLimitExceededException("The MySkoda Public API rate limit (per vehicle and hour) is almost "
                + "used up and no cached vehicle data is available yet. " + tryAgain(retryAfter), null, null,
                retryAfter);
    }

    /** How long to wait before the next request ({@code Retry-After}), if the API said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}