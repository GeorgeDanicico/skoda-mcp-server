package com.gd.skoda.upstream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Any other failure: an unexpected error status (e.g. 400) or a response that could not be read.
 * Usually a bug in this application or an incompatible change of the upstream API.
 */
public final class UnexpectedUpstreamResponseException extends SkodaApiException {

    public UnexpectedUpstreamResponseException(HttpStatusCode upstreamStatus, @Nullable ProblemDetail problem) {
        super(withUpstreamDetail("Unexpected response from the MySkoda Public API (HTTP " + upstreamStatus.value()
                + "). If this persists, check for an update of this application.", problem),
                upstreamStatus, problem, null);
    }

    public UnexpectedUpstreamResponseException(String reason, Throwable cause) {
        super("Unexpected response from the MySkoda Public API: " + reason
                + ". If this persists, check for an update of this application.", null, null, cause);
    }
}