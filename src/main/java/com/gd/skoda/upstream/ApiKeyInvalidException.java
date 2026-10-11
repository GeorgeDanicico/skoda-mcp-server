package com.gd.skoda.upstream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** 401 for any reason other than expiry: the API key is unknown, revoked or malformed. */
public final class ApiKeyInvalidException extends SkodaApiException {

    public ApiKeyInvalidException(HttpStatusCode upstreamStatus, @Nullable ProblemDetail problem) {
        super(withUpstreamDetail("The MySkoda Public API rejected the API key. Check SKODA_API_KEY, or create "
                + "a new API key in the MySkoda app and set it as SKODA_API_KEY.", problem),
                upstreamStatus, problem, null);
    }
}