package com.gd.skoda.error;

import java.net.URI;

/**
 * RFC 9457 problem {@code type} URIs of this application (non-dereferenceable URNs). Clients can switch
 * on them; titles and details are for humans.
 */
public final class ProblemTypes {

    private static final String PREFIX = "urn:skoda-mcp-server:problem:";

    /** 503: the server's MyŠkoda API key has expired. */
    public static final URI API_KEY_EXPIRED = type("api-key-expired");
    /** 503: MyŠkoda rejected the server's API key. */
    public static final URI API_KEY_INVALID = type("api-key-invalid");
    /** 503: the server's API key does not cover the configured vehicle. */
    public static final URI API_KEY_NOT_AUTHORIZED = type("api-key-not-authorized");
    /** 429: upstream rate limit used up (or the vehicle declined) and nothing cached. */
    public static final URI RATE_LIMIT_EXCEEDED = type("rate-limit-exceeded");
    /** 502: MyŠkoda does not know the configured VIN. */
    public static final URI VEHICLE_NOT_FOUND = type("vehicle-not-found");
    /** 503: MyŠkoda returned 5xx or could not be reached. */
    public static final URI UPSTREAM_UNAVAILABLE = type("upstream-unavailable");
    /** 504: MyŠkoda did not answer in time. */
    public static final URI UPSTREAM_TIMEOUT = type("upstream-timeout");
    /** 502: MyŠkoda answered with an unexpected status or unreadable body. */
    public static final URI UPSTREAM_UNEXPECTED_RESPONSE = type("upstream-unexpected-response");
    /** 500: a bug in this server. */
    public static final URI INTERNAL_ERROR = type("internal-error");

    private ProblemTypes() {
    }

    private static URI type(String slug) {
        return URI.create(PREFIX + slug);
    }
}
