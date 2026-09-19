package com.gd.skoda.mcp;

import com.gd.skoda.api.client.Vehicle;
import com.gd.skoda.api.client.VehicleLocation;
import com.gd.skoda.api.client.VehicleRange;
import com.gd.skoda.api.client.VehicleService;
import com.gd.skoda.api.client.VehicleStatus;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkodaTools {

    private final String email;
    private final String password;
    private final String vin;
    private final String spin;
    private final String baseUrl;
    private volatile VehicleService vehicleService;
    private final SkodaVehicleFacade facade;

    @Autowired
    public SkodaTools(SkodaVehicleFacade facade) {
        this.email = null;
        this.password = null;
        this.vin = null;
        this.spin = null;
        this.baseUrl = null;
        this.facade = facade;
    }

    public SkodaTools(
            @Value("${skoda.email}") String email,
            @Value("${skoda.password}") String password,
            @Value("${skoda.vin}") String vin,
            @Value("${skoda.spin}") String spin,
            @Value("${skoda.api-base-url:}") String baseUrl) {
        this.email = email;
        this.password = password;
        this.vin = vin;
        this.spin = spin;
        this.baseUrl = baseUrl;
        this.facade = null;
    }

    @McpTool(
            name = "list_vehicles",
            description = "List the Skoda vehicles belonging to the configured account, including their VINs.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false)
    )
    public List<Vehicle> listVehicles() {
        if (facade != null) return facade.vehicles();
        return vehicleService().getVehicles();
    }

    @McpTool(
            name = "get_vehicle_location",
            description = "Get the last reported geographic location of a Skoda vehicle.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false)
    )
    public VehicleLocation getVehicleLocation(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) return facade.location();
        String targetVin = resolvedVin(vin);
        return vehicleService().getVehicleLocation(targetVin);
    }

    @McpTool(
            name = "get_vehicle_status",
            description = "Get the last reported door, window, light, lock, trunk, bonnet, and sunroof states.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false)
    )
    public VehicleStatus getVehicleStatus(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) return facade.status();
        String targetVin = resolvedVin(vin);
        return vehicleService().getVehicleStatus(targetVin);
    }

    @McpTool(
            name = "get_vehicle_range",
            description = "Get the last reported driving range and battery state of charge.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false)
    )
    public VehicleRange getVehicleRange(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) return facade.range();
        String targetVin = resolvedVin(vin);
        return vehicleService().getVehicleRange(targetVin);
    }

    @McpTool(
            name = "flash_vehicle_lights",
            description = "Flash a Skoda vehicle's lights once. This causes an immediate physical action.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String flashVehicleLights(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) { facade.flash(); return "The vehicle lights were flashed successfully."; }
        String targetVin = resolvedVin(vin);
        vehicleService().flashVehicleLights(targetVin);
        return "The vehicle lights were flashed successfully.";
    }

    @McpTool(
            name = "honk_and_flash_vehicle",
            description = "Honk a Skoda vehicle's horn and flash its lights. This causes an immediate physical action.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false)
    )
    public String honkAndFlashVehicle(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) { facade.honkAndFlash(); return "The vehicle horn and lights were activated successfully."; }
        String targetVin = resolvedVin(vin);
        vehicleService().honkAndFlashVehicle(targetVin);
        return "The vehicle horn and lights were activated successfully.";
    }

    @McpTool(
            name = "lock_vehicle",
            description = "Lock a Skoda vehicle using the configured S-PIN.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true
            )
    )
    public String lockVehicle(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) { facade.lock(); return "The vehicle was locked successfully."; }
        String targetVin = resolvedVin(vin);
        String configuredSpin = validatedSpin();
        vehicleService().lockVehicle(targetVin, configuredSpin);
        return "The vehicle was locked successfully.";
    }

    @McpTool(
            name = "unlock_vehicle",
            description = "Unlock a Skoda vehicle using the configured S-PIN.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true
            )
    )
    public String unlockVehicle(
            @McpToolParam(description = "Optional VIN override. Uses the configured skoda.vin by default.", required = false)
            String vin) {
        if (facade != null) { facade.unlock(); return "The vehicle was unlocked successfully."; }
        String targetVin = resolvedVin(vin);
        String configuredSpin = validatedSpin();
        vehicleService().unlockVehicle(targetVin, configuredSpin);
        return "The vehicle was unlocked successfully.";
    }

    @McpTool(
            name = "get_vehicle_snapshot",
            description = "Get a concurrent, normalized snapshot for the configured vehicle. No VIN is accepted.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false)
    )
    public VehicleSnapshot getVehicleSnapshot() {
        if (facade == null) throw new IllegalStateException("Snapshot facade is unavailable");
        return facade.snapshot();
    }

    private VehicleService vehicleService() {
        VehicleService current = vehicleService;
        if (current == null) {
            synchronized (this) {
                current = vehicleService;
                if (current == null) {
                    String configuredEmail = requiredProperty("skoda.email", email);
                    String configuredPassword = requiredProperty("skoda.password", password);

                    current = baseUrl == null || baseUrl.isBlank()
                            ? new VehicleService(configuredEmail, configuredPassword)
                            : new VehicleService(configuredEmail, configuredPassword, baseUrl);
                    vehicleService = current;
                }
            }
        }
        return current;
    }

    private String validatedSpin() {
        String spin = requiredProperty("skoda.spin", this.spin);
        if (!spin.matches("[0-9]{4}")) {
            throw new IllegalStateException("skoda.spin must contain exactly four digits");
        }
        return spin;
    }

    private String resolvedVin(String vinOverride) {
        return requiredProperty("skoda.vin", vinOverride == null || vinOverride.isBlank() ? vin : vinOverride);
    }

    private String requiredProperty(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured before using Skoda tools");
        }
        return value;
    }
}
