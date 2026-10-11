package com.gd.skoda.vehicle;

import com.gd.skoda.upstream.PublicApiClient;
import com.gd.skoda.upstream.publicapi.api.VehiclesApi;
import com.gd.skoda.upstream.publicapi.model.VehiclePart;
import com.gd.skoda.upstream.publicapi.model.VehicleResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;

/**
 * In-memory stand-in for the generated {@link VehiclesApi}, wrapped in the real {@link PublicApiClient}
 * facade. Counts calls and answers with whatever the current behaviour supplies (or throws).
 */
final class FakeUpstream implements VehiclesApi {

    static final String VIN = "TMBJJ7NE8L0123456";

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Supplier<VehicleResponse>> behaviour =
            new AtomicReference<>(UpstreamFixtures::full);
    private final AtomicReference<List<VehiclePart>> lastInclude = new AtomicReference<>();

    final PublicApiClient client = new PublicApiClient(this, VIN);

    @Override
    public ResponseEntity<VehicleResponse> getVehicle(String vin, List<VehiclePart> include) {
        calls.incrementAndGet();
        lastInclude.set(include);
        return ResponseEntity.ok(behaviour.get().get());
    }

    /** Answer every following call with this response. */
    void respondWith(VehicleResponse response) {
        behaviour.set(() -> response);
    }

    /** Answer every following call with this supplier (which may throw or block). */
    void respondWith(Supplier<VehicleResponse> supplier) {
        behaviour.set(supplier);
    }

    /** Fail every following call with this exception. */
    void failWith(RuntimeException exception) {
        behaviour.set(() -> {
            throw exception;
        });
    }

    int calls() {
        return calls.get();
    }

    List<VehiclePart> lastInclude() {
        return lastInclude.get();
    }
}
