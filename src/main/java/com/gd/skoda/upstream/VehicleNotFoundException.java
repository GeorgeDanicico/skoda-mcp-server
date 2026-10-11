package com.gd.skoda.upstream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** 404: the upstream API knows no vehicle with the configured VIN. */
public final class VehicleNotFoundException extends SkodaApiException {

    public VehicleNotFoundException(HttpStatusCode upstreamStatus, @Nullable ProblemDetail problem) {
        super(withUpstreamDetail("No vehicle found for the configured VIN. Check SKODA_VIN, and that the API key "
                + "in the MySkoda app was created for this vehicle.", problem), upstreamStatus, problem, null);
    }
}