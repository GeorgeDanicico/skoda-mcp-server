package com.gd.skoda.mcp;

import com.gd.skoda.api.client.Vehicle;
import com.gd.skoda.api.client.VehicleLocation;
import com.gd.skoda.api.client.VehicleRange;
import com.gd.skoda.api.client.VehicleService;
import com.gd.skoda.api.client.VehicleStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The single application layer shared by the HTTP gateway and MCP tools.
 * It owns the long-lived API client, cache, configured VIN, and explicit
 * vehicle actions; neither transport may select another vehicle or S-PIN.
 */
@Component
public class SkodaVehicleFacade {

    private static final Logger log = LoggerFactory.getLogger(SkodaVehicleFacade.class);

    private final String email;
    private final String password;
    private final String vin;
    private final String spin;
    private final String baseUrl;
    private final Duration snapshotTtl;
    private final Duration snapshotTimeout;
    private volatile VehicleService vehicleService;
    private volatile Timed<Identity> identityCache;
    private volatile Timed<VehicleSnapshot> snapshotCache;

    public SkodaVehicleFacade(
            @Value("${skoda.email}") String email,
            @Value("${skoda.password}") String password,
            @Value("${skoda.vin}") String vin,
            @Value("${skoda.spin}") String spin,
            @Value("${skoda.api-base-url:}") String baseUrl,
            @Value("${skoda.snapshot-cache-seconds:5}") long snapshotCacheSeconds,
            @Value("${skoda.snapshot-timeout-seconds:18}") long snapshotTimeoutSeconds) {
        this.email = email;
        this.password = password;
        this.vin = vin;
        this.spin = spin;
        this.baseUrl = baseUrl;
        this.snapshotTtl = Duration.ofSeconds(Math.max(0, snapshotCacheSeconds));
        this.snapshotTimeout = Duration.ofSeconds(Math.max(1, snapshotTimeoutSeconds));
    }

    public VehicleSnapshot snapshot() {
        Timed<VehicleSnapshot> cached = snapshotCache;
        if (cached != null && cached.isFresh(snapshotTtl)) {
            return cached.value();
        }
        synchronized (this) {
            cached = snapshotCache;
            if (cached != null && cached.isFresh(snapshotTtl)) {
                return cached.value();
            }
            VehicleSnapshot snapshot = retrieveSnapshot();
            snapshotCache = new Timed<>(snapshot);
            return snapshot;
        }
    }

    /** Populate the shared token and stable identity caches before serving traffic. */
    public void warmUp() {
        long startedAt = System.nanoTime();
        log.info("vehicle_gateway_warmup_started");
        service().authenticate();
        try {
            identity();
        } catch (RuntimeException exc) {
            // Identity is cosmetic. A missing optional field such as a license
            // plate must not prevent status, range, location, or actions.
            log.warn("vehicle_identity_warmup_failed error_type={}", exc.getClass().getSimpleName());
        }
        log.info("vehicle_gateway_warmup_completed duration_ms={}",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    public VehicleLocation location() { return service().getVehicleLocation(configuredVin()); }
    public VehicleStatus status() { return service().getVehicleStatus(configuredVin()); }
    public VehicleRange range() { return service().getVehicleRange(configuredVin()); }
    public List<Vehicle> vehicles() { return service().getVehicles(); }
    public void flash() { service().flashVehicleLights(configuredVin()); }
    public void honkAndFlash() { service().honkAndFlashVehicle(configuredVin()); }
    public void lock() { service().lockVehicle(configuredVin(), configuredSpin()); }
    public void unlock() { service().unlockVehicle(configuredVin(), configuredSpin()); }

    private VehicleSnapshot retrieveSnapshot() {
        long startedAt = System.nanoTime();
        long deadline = startedAt + snapshotTimeout.toNanos();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Identity> identity = executor.submit(() -> timed("identity", this::identity));
            Future<VehicleLocation> location = executor.submit(() -> timed("location", this::location));
            Future<VehicleStatus> status = executor.submit(() -> timed("status", this::status));
            Future<VehicleRange> range = executor.submit(() -> timed("range", this::range));
            List<String> unavailable = new ArrayList<>();
            Identity identityValue = await(identity, "identity", unavailable, deadline);
            VehicleLocation locationValue = await(location, "location", unavailable, deadline);
            VehicleStatus statusValue = await(status, "status", unavailable, deadline);
            VehicleRange rangeValue = await(range, "range", unavailable, deadline);
            if (locationValue == null && statusValue == null && rangeValue == null) {
                throw new IllegalStateException("All vehicle snapshot sections failed");
            }
            String timestamp = rangeValue != null ? rangeValue.getCarCapturedTimestamp()
                    : statusValue != null ? statusValue.getCarCapturedTimestamp() : null;
            VehicleSnapshot snapshot = new VehicleSnapshot(
                    identityValue == null ? null : identityValue.name(),
                    identityValue == null ? null : identityValue.licensePlate(),
                    rangeValue == null ? null : rangeValue.getRemainingRangeInKm(),
                    rangeValue == null ? null : rangeValue.getCurrentSoCInPercent(),
                    statusValue == null ? null : statusValue.getDoorsLocked(),
                    statusValue == null ? null : statusValue.getDoors(),
                    statusValue == null ? null : statusValue.getWindows(),
                    statusValue == null ? null : statusValue.getTrunk(),
                    statusValue == null ? null : statusValue.getBonnet(),
                    statusValue == null ? null : statusValue.getLights(),
                    locationValue == null ? null : new VehicleSnapshot.Location(
                            locationValue.getLatitude(), locationValue.getLongitude(), locationValue.getAddress()),
                    timestamp,
                    !unavailable.isEmpty(),
                    List.copyOf(unavailable));
            log.info("vehicle_snapshot_completed duration_ms={} partial={} unavailable={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    snapshot.partial(), snapshot.unavailableSections());
            return snapshot;
        } finally {
            // ExecutorService.close() waits for every task to finish. That can
            // violate the snapshot deadline when an HTTP call does not react
            // promptly to interruption. Cancellation is best-effort; return
            // control to the HTTP layer immediately at the configured deadline.
            executor.shutdownNow();
        }
    }

    private <T> T await(Future<T> future, String section, List<String> unavailable, long deadlineNanos) {
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                future.cancel(true);
                unavailable.add(section);
                return null;
            }
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrieving vehicle snapshot", exc);
        } catch (ExecutionException | java.util.concurrent.TimeoutException exc) {
            future.cancel(true);
            unavailable.add(section);
            return null;
        }
    }

    private <T> T timed(String section, Supplier<T> operation) {
        long startedAt = System.nanoTime();
        try {
            T result = operation.get();
            log.info("vehicle_snapshot_section_completed section={} duration_ms={}", section,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return result;
        } catch (RuntimeException exc) {
            log.warn("vehicle_snapshot_section_failed section={} duration_ms={} error_type={}", section,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), exc.getClass().getSimpleName());
            throw exc;
        }
    }

    private Identity identity() {
        Timed<Identity> cached = identityCache;
        if (cached != null && cached.isFresh(Duration.ofHours(1))) {
            return cached.value();
        }
        Vehicle vehicle = service().getVehicles().stream()
                .filter(candidate -> configuredVin().equals(candidate.getVin()))
                .findFirst()
                .orElse(null);
        Identity identity = vehicle == null ? null : new Identity(
                vehicle.getName() == null || vehicle.getName().isBlank() ? vehicle.getTitle() : vehicle.getName(),
                vehicle.getLicensePlate());
        identityCache = new Timed<>(identity);
        return identity;
    }

    private VehicleService service() {
        VehicleService current = vehicleService;
        if (current == null) {
            synchronized (this) {
                current = vehicleService;
                if (current == null) {
                    String configuredEmail = required("skoda.email", email);
                    String configuredPassword = required("skoda.password", password);
                    current = baseUrl == null || baseUrl.isBlank()
                            ? new VehicleService(configuredEmail, configuredPassword)
                            : new VehicleService(configuredEmail, configuredPassword, baseUrl);
                    vehicleService = current;
                }
            }
        }
        return current;
    }

    private String configuredVin() { return required("skoda.vin", vin); }
    private String configuredSpin() {
        String configured = required("skoda.spin", spin);
        if (!configured.matches("[0-9]{4}")) {
            throw new IllegalStateException("skoda.spin must contain exactly four digits");
        }
        return configured;
    }
    private static String required(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured before using Skoda tools");
        return value;
    }
    private record Identity(String name, String licensePlate) {}
    private record Timed<T>(T value, long createdAtNanos) {
        Timed(T value) { this(value, System.nanoTime()); }
        boolean isFresh(Duration ttl) { return System.nanoTime() - createdAtNanos <= ttl.toNanos(); }
    }
}
