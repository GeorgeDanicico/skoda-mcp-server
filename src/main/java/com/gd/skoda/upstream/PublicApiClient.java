package com.gd.skoda.upstream;

import com.gd.skoda.upstream.publicapi.api.VehiclesApi;
import com.gd.skoda.upstream.publicapi.model.VehiclePart;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Facade over the generated MyŠkoda Public API client for the configured vehicle. The rest of the
 * application uses this class rather than the generated {@link VehiclesApi}.
 *
 * <p>Every call counts against the upstream rate limit (20 requests per hour and vehicle), so callers
 * should cache results. Failures are reported as {@link SkodaApiException} subclasses.
 */
public final class PublicApiClient {

    private final VehiclesApi vehiclesApi;
    private final String vin;

    public PublicApiClient(VehiclesApi vehiclesApi, String vin) {
        Assert.hasText(vin, "vin must not be blank");
        this.vehiclesApi = vehiclesApi;
        this.vin = vin;
    }

    /**
     * Fetches the configured vehicle with one upstream call ({@code GET /api/v1/vehicles/{vin}}).
     *
     * @param parts the parts to include; empty means all parts the vehicle supports. Requested parts
     *              may still be absent from the response, in which case
     *              {@link VehicleResponse#getErrors()} explains why.
     * @return the vehicle response, never {@code null}
     * @throws SkodaApiException if the call fails
     */
    public VehicleResponse fetchVehicle(Set<VehiclePart> parts) {
        // Declaration order keeps the query string stable, e.g. include=info,status,fuelStatus
        List<VehiclePart> include = parts.isEmpty() ? null : parts.stream().sorted().toList();
        ResponseEntity<VehicleResponse> response;
        try {
            response = vehiclesApi.getVehicle(vin, include);
        } catch (ResourceAccessException ex) {
            throw UpstreamUnavailableException.forIoError(ex.getCause() != null ? ex.getCause() : ex);
        } catch (RestClientException ex) {
            throw new UnexpectedUpstreamResponseException("the vehicle response could not be read", ex);
        }
        VehicleResponse body = response.getBody();
        if (body == null || body.getVehicle() == null) {
            throw new UnexpectedUpstreamResponseException("the vehicle response is empty",
                    new IllegalStateException("HTTP " + response.getStatusCode().value() + " without vehicle"));
        }
        return body;
    }

    /** The VIN this client fetches. */
    public String vin() {
        return vin;
    }
}
