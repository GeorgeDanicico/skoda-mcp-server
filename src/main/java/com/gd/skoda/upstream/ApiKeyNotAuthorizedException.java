package com.gd.skoda.upstream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** 403 ({@code api-key-not-authorized}): the API key does not cover the configured vehicle. */
public final class ApiKeyNotAuthorizedException extends SkodaApiException {

    public ApiKeyNotAuthorizedException(HttpStatusCode upstreamStatus, @Nullable ProblemDetail problem) {
        super(withUpstreamDetail("The API key is not authorized for the configured vehicle. In the MySkoda app, "
                + "create or update the API key so that it includes the vehicle set as SKODA_VIN.", problem),
                upstreamStatus, problem, null);
    }
}