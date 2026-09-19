package com.gd.skoda.api.client;

import com.gd.skoda.ApiClient;
import com.gd.skoda.ApiException;
import com.gd.skoda.client.AirConditioningApi;
import com.gd.skoda.client.ChargingApi;
import com.gd.skoda.client.LocationApi;
import com.gd.skoda.client.RangeApi;
import com.gd.skoda.client.StatusApi;
import com.gd.skoda.client.VehicleApi;
import com.gd.skoda.client.VehicleAccessApi;
import com.gd.skoda.client.resource.AirConditioningWebRequestResource;
import com.gd.skoda.client.resource.AirConditioningWebResponseResource;
import com.gd.skoda.client.resource.ChargingSessionWebResponseResource;
import com.gd.skoda.client.resource.ChargingStatusWebResponseResource;
import com.gd.skoda.client.resource.LocationWebResponseResource;
import com.gd.skoda.client.resource.RangeWebResponseResource;
import com.gd.skoda.client.resource.StatusWebResponseResource;
import com.gd.skoda.client.resource.VehicleWebResponseResource;
import com.gd.skoda.client.resource.VehicleAccessSecurityWebRequestResource;

import java.util.List;

/**
 * Service for interacting with Škoda vehicles via the unofficial Škoda API.
 * <p>
 * Provides methods to retrieve vehicle information such as location, status, range,
 * and air conditioning state, as well as control features like starting and stopping
 * air conditioning.
 * </p>
 * <p>
 * Authentication is handled internally using the provided email and password credentials.
 * All methods throw a {@link VehicleServiceException} if the underlying API call fails.
 * </p>
 */
public class VehicleService {

    private final ClientConfiguration clientConfiguration;
    private final VehicleApi vehicleApi;
    private final LocationApi locationApi;
    private final StatusApi statusApi;
    private final RangeApi rangeApi;
    private final AirConditioningApi airConditioningApi;
    private final ChargingApi chargingApi;
    private final VehicleAccessApi vehicleAccessApi;

    /**
     * Constructs a new {@code VehicleService} using the default server configuration.
     *
     * @param email    the email address used for authentication.
     * @param password the password used for authentication.
     */
    public VehicleService(String email, String password) {
        ClientConfiguration clientConfiguration = new ClientConfiguration(email, password);
        this.clientConfiguration = clientConfiguration;
        this.vehicleApi = new VehicleApi(clientConfiguration.getApiClient());
        this.locationApi = new LocationApi(clientConfiguration.getApiClient());
        this.statusApi = new StatusApi(clientConfiguration.getApiClient());
        this.rangeApi = new RangeApi(clientConfiguration.getApiClient());
        this.airConditioningApi = new AirConditioningApi(clientConfiguration.getApiClient());
        this.chargingApi = new ChargingApi(clientConfiguration.getApiClient());
        this.vehicleAccessApi = new VehicleAccessApi(clientConfiguration.getApiClient());
    }

    /**
     * Constructs a new {@code VehicleService} using a custom server URL.
     * <p>
     * Use this constructor if you want to point the client to a non-default API server,
     * for example, a local mock or a staging environment.
     * </p>
     *
     * @param email    the email address used for authentication.
     * @param password the password used for authentication.
     * @param server   the base URL of the API server to use.
     */
    public VehicleService(String email, String password, String server) {
        ClientConfiguration clientConfiguration = new ClientConfiguration(email, password, server);
        this.clientConfiguration = clientConfiguration;
        this.vehicleApi = new VehicleApi(clientConfiguration.getApiClient());
        this.locationApi = new LocationApi(clientConfiguration.getApiClient());
        this.statusApi = new StatusApi(clientConfiguration.getApiClient());
        this.rangeApi = new RangeApi(clientConfiguration.getApiClient());
        this.airConditioningApi = new AirConditioningApi(clientConfiguration.getApiClient());
        this.chargingApi = new ChargingApi(clientConfiguration.getApiClient());
        this.vehicleAccessApi = new VehicleAccessApi(clientConfiguration.getApiClient());
    }

    VehicleService(ApiClient apiClient) {
        this.clientConfiguration = null;
        this.vehicleApi = new VehicleApi(apiClient);
        this.locationApi = new LocationApi(apiClient);
        this.statusApi = new StatusApi(apiClient);
        this.rangeApi = new RangeApi(apiClient);
        this.airConditioningApi = new AirConditioningApi(apiClient);
        this.chargingApi = new ChargingApi(apiClient);
        this.vehicleAccessApi = new VehicleAccessApi(apiClient);
    }

    /** Authenticate eagerly so persistent services do not pay login latency on a user request. */
    public void authenticate() {
        if (clientConfiguration != null) {
            clientConfiguration.authenticate();
        }
    }

    /**
     * Retrieves all vehicles associated with the authenticated account.
     *
     * @return a list of {@link Vehicle} objects representing the user's vehicles.
     * @throws VehicleServiceException if the API call fails.
     */
    public List<Vehicle> getVehicles() {
        try {
            List<VehicleWebResponseResource> vehicleWebResponseResources = vehicleApi.findAllVehicles();
            return vehicleWebResponseResources.stream()
                    .map(this::mapToVehicle)
                    .toList();
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to retrieve vehicles", e.getMessage());
        }
    }

    /**
     * Retrieves the current location of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @return a {@link VehicleLocation} containing the latitude, longitude, and address of the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public VehicleLocation getVehicleLocation(String vin) {
        try {
            LocationWebResponseResource location = locationApi.getLocation(vin);
            return new VehicleLocation(location.getLatitude(), location.getLongitude(), location.getAddress());
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to get vehicle location", e.getMessage());
        }
    }

    /**
     * Retrieves the current status of a vehicle, including door, window, and lock states.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @return a {@link VehicleStatus} containing detailed status information about the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public VehicleStatus getVehicleStatus(String vin) {
        try {
            StatusWebResponseResource status = statusApi.getStatus(vin);
            return new VehicleStatus(status.getDoorsLocked(), status.getLocked(), status.getDoors(), status.getWindows(),
                    status.getLights(), status.getReliableLockStatus(), status.getSunroof(), status.getTrunk(),
                    status.getBonnet(), status.getCarCapturedTimestamp());
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to get vehicle status", e.getMessage());
        }
    }

    /**
     * Retrieves the current range and battery information of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @return a {@link VehicleRange} containing the car type, total range, engine type,
     *         current state of charge, remaining range, and the timestamp of the last update.
     * @throws VehicleServiceException if the API call fails.
     */
    public VehicleRange getVehicleRange(String vin) {
        try {
            RangeWebResponseResource range = rangeApi.getRange(vin);
            return new VehicleRange(range.getCarType(), range.getTotalRangeInKm(), range.getEngineType(),
                    range.getCurrentSoCInPercent(), range.getRemainingRangeInKm(), range.getCarCapturedTimestamp());
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to get vehicle range", e.getMessage());
        }
    }

    /**
     * Retrieves the current air conditioning status of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @return a {@link VehicleAirConditioningStatus} containing the state, temperature,
     *         temperature unit, and the timestamp of the last update.
     * @throws VehicleServiceException if the API call fails.
     */
    public VehicleAirConditioningStatus getVehicleAirConditioning(String vin) {
        try {
            AirConditioningWebResponseResource airConditioning = airConditioningApi.getAirConditioning(vin);
            return new VehicleAirConditioningStatus(airConditioning.getState(), airConditioning.getTemperature(),
                    airConditioning.getTemperatureUnit(), airConditioning.getCarCapturedTimestamp());
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to get vehicle air conditioning", e.getMessage());
        }
    }

    /**
     * Starts the air conditioning of a vehicle with the specified settings.
     *
     * @param vin             the Vehicle Identification Number of the vehicle.
     * @param heaterSource    the heat source to use (e.g., electric).
     * @param temperature     the desired target temperature.
     * @param temperatureUnit the unit of the target temperature (e.g., Celsius).
     * @throws VehicleServiceException if the API call fails.
     */
    public void startVehicleAirConditioning(String vin, VehicleHeaterSource heaterSource, double temperature, VehicleTemperatureUnit temperatureUnit) {
        AirConditioningWebRequestResource requestResource = new AirConditioningWebRequestResource();
        requestResource.setHeaterSource(heaterSource.toString());
        requestResource.setTemperature(temperature);
        requestResource.setTemperatureUnit(temperatureUnit.toString());

        try {
            airConditioningApi.startAirConditioning(vin, requestResource);
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to start vehicle air conditioning", e.getMessage());
        }
    }

    /**
     * Stops the air conditioning of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public void stopVehicleAirConditioning(String vin) {
        try {
            airConditioningApi.stopAirConditioning(vin);
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to stop vehicle air conditioning", e.getMessage());
        }
    }

    /**
     * Retrieves the current charging state of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @return a {@link VehicleChargingState} containing details such as charging rate, charge power,
     *         remaining time to fully charge, charging state, charge type, remaining cruising range,
     *         and the current state of charge.
     * @throws VehicleServiceException if the API call fails.
     */
    public VehicleChargingState getVehicleChargingState(String vin) {
        try {
            ChargingStatusWebResponseResource chargingStatus = chargingApi.getChargingStatus(vin);
            return new VehicleChargingState(chargingStatus.getChargingRateInKilometersPerHour(),
                    chargingStatus.getChargePowerInKw(),
                    chargingStatus.getRemainingTimeToFullyChargedInMinutes(),
                    chargingStatus.getState(),
                    chargingStatus.getChargeType(),
                    chargingStatus.getRemainingCruisingRangeInMeters(),
                    chargingStatus.getStateOfChargeInPercent(),
                    chargingStatus.getCarCapturedTimestamp());
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to get vehicle charging state", e.getMessage());
        }
    }

    /**
     * Retrieves the charging session history of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @return a list of {@link VehicleChargingSession} objects, each containing details such as
     *         start time, energy charged in kWh, duration in minutes, and current type.
     * @throws VehicleServiceException if the API call fails.
     */
    public List<VehicleChargingSession> getVehicleChargingSessions(String vin) {
        try {
            List<ChargingSessionWebResponseResource> sessions = chargingApi.getChargingSession(vin);
            return sessions.stream()
                    .map(session -> new VehicleChargingSession(
                            session.getStartAt(), session.getChargedInKWh(), session.getDurationInMinutes(), session.getCurrentType())).toList();
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to get vehicle charging sessions", e.getMessage());
        }
    }

    /**
     * Starts a charging session for a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public void startCharging(String vin) {
        try {
            chargingApi.startCharging(vin);
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to start charging", e.getMessage());
        }
    }

    /**
     * Stops the active charging session of a vehicle.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public void stopCharging(String vin) {
        try {
            chargingApi.stopCharging(vin);
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to stop charging", e.getMessage());
        }
    }

    /**
     * Flashes the vehicle's lights using its current reported position.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public void flashVehicleLights(String vin) {
        try {
            vehicleAccessApi.flash(vin);
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to flash vehicle lights", e.getMessage());
        }
    }

    /**
     * Honks the vehicle's horn and flashes its lights using its current reported position.
     *
     * @param vin the Vehicle Identification Number of the vehicle.
     * @throws VehicleServiceException if the API call fails.
     */
    public void honkAndFlashVehicle(String vin) {
        try {
            vehicleAccessApi.honkAndFlash(vin);
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to honk and flash vehicle", e.getMessage());
        }
    }

    /**
     * Locks the vehicle using the four-digit MySkoda security PIN (S-PIN).
     *
     * @param vin  the Vehicle Identification Number of the vehicle.
     * @param spin the four-digit MySkoda security PIN.
     * @throws IllegalArgumentException if {@code spin} is not exactly four digits.
     * @throws VehicleServiceException if the API call fails.
     */
    public void lockVehicle(String vin, String spin) {
        try {
            vehicleAccessApi.lock(vin, securityRequest(spin));
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to lock vehicle", e.getMessage());
        }
    }

    /**
     * Unlocks the vehicle using the four-digit MySkoda security PIN (S-PIN).
     *
     * @param vin  the Vehicle Identification Number of the vehicle.
     * @param spin the four-digit MySkoda security PIN.
     * @throws IllegalArgumentException if {@code spin} is not exactly four digits.
     * @throws VehicleServiceException if the API call fails.
     */
    public void unlockVehicle(String vin, String spin) {
        try {
            vehicleAccessApi.unlock(vin, securityRequest(spin));
        } catch (ApiException e) {
            throw new VehicleServiceException("Failed to unlock vehicle", e.getMessage());
        }
    }

    private VehicleAccessSecurityWebRequestResource securityRequest(String spin) {
        if (spin == null || !spin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("spin must contain exactly four digits");
        }

        VehicleAccessSecurityWebRequestResource request = new VehicleAccessSecurityWebRequestResource();
        request.setSpin(spin);
        return request;
    }

    /**
     * Maps a {@link VehicleWebResponseResource} to a {@link Vehicle} domain object.
     *
     * @param vehicleWebResponseResource the API response resource to map.
     * @return a {@link Vehicle} containing the VIN, name, title, and license plate.
     */
    private Vehicle mapToVehicle(VehicleWebResponseResource vehicleWebResponseResource) {
        return new Vehicle(vehicleWebResponseResource.getVin(),
                vehicleWebResponseResource.getName(),
                vehicleWebResponseResource.getTitle(),
                vehicleWebResponseResource.getLicensePlate());
    }
}
