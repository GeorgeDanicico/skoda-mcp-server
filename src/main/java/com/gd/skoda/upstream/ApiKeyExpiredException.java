package com.gd.skoda.upstream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** 401 with problem type {@code api-key-expired}: the API key must be replaced. */
public final class ApiKeyExpiredException extends SkodaApiException {

    public ApiKeyExpiredException(HttpStatusCode upstreamStatus, @Nullable ProblemDetail problem) {
        super(withUpstreamDetail("The MySkoda API key has expired. Create a new API key in the MySkoda app "
                + "and set it as SKODA_API_KEY.", problem), upstreamStatus, problem, null);
    }
}