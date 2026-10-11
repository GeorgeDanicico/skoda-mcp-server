package com.gd.skoda.api;

import com.gd.skoda.api.generated.model.ApiKeyInfoDto;
import com.gd.skoda.api.generated.model.VehicleLocationResponseDto;
import com.gd.skoda.api.generated.model.VehicleOverviewResponseDto;
import com.gd.skoda.api.generated.model.VehicleRangeResponseDto;
import com.gd.skoda.api.generated.model.VehicleStatusResponseDto;
import com.gd.skoda.upstream.SkodaApiException;
import com.gd.skoda.vehicle.VehicleService;

/**
 * The operations of {@code skoda-service-api.yaml}, returning the generated DTOs. One method per
 * {@code operationId}, with the same name, so generated callers can use {@code facade.{{operationId}}()}.
 *
 * <p>Profile-independent and free of web types: the REST controllers ({@code api} profile) wrap the
 * results in {@code ResponseEntity}s, and the MCP tools ({@code mcp} profile) return them as they are.
 * Failures surface as {@link SkodaApiException} subclasses; each caller maps them to its own error format.
 */
public final class VehicleApiFacade {

    private final VehicleService vehicleService;
    private final ServiceApiMapper mapper;

    public VehicleApiFacade(VehicleService vehicleService, ServiceApiMapper mapper) {
        this.vehicleService = vehicleService;
        this.mapper = mapper;
    }

    /** {@code getVehicleOverview}: everything known about the vehicle. */
    public VehicleOverviewResponseDto getVehicleOverview() {
        return mapper.toOverviewResponse(vehicleService.getOverview());
    }

    /** {@code getVehicleLocation}: parking position or in motion. */
    public VehicleLocationResponseDto getVehicleLocation() {
        return mapper.toLocationResponse(vehicleService.getLocation());
    }

    /** {@code getVehicleRange}: driving range and energy levels. */
    public VehicleRangeResponseDto getVehicleRange() {
        return mapper.toRangeResponse(vehicleService.getRange());
    }

    /** {@code getVehicleStatus}: lock/door/window/light state and odometer. */
    public VehicleStatusResponseDto getVehicleStatus() {
        return mapper.toStatusResponse(vehicleService.getStatus());
    }

    /** {@code getApiKeyStatus}: API key expiry and rate-limit state; never calls upstream. */
    public ApiKeyInfoDto getApiKeyStatus() {
        return mapper.toDto(vehicleService.getApiKeyInfo());
    }
}
