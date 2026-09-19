package com.gd.skoda.mcp;

import com.gd.skoda.api.client.Vehicle;
import com.gd.skoda.api.client.VehicleLocation;
import com.gd.skoda.api.client.VehicleRange;
import com.gd.skoda.api.client.VehicleService;
import com.gd.skoda.api.client.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkodaVehicleFacadeTest {
    private static final String VIN = "TESTVIN0000000000";

    private VehicleService service;
    private SkodaVehicleFacade facade;

    @BeforeEach
    void setUp() {
        service = mock(VehicleService.class);
        facade = new SkodaVehicleFacade("email", "password", VIN, "1234", "", 5, 2);
        ReflectionTestUtils.setField(facade, "vehicleService", service);
    }

    @Test
    void retrievesIndependentSnapshotSectionsConcurrently() throws Exception {
        Vehicle vehicle = mock(Vehicle.class);
        VehicleLocation location = mock(VehicleLocation.class);
        VehicleStatus status = mock(VehicleStatus.class);
        VehicleRange range = mock(VehicleRange.class);
        when(vehicle.getVin()).thenReturn(VIN);
        when(vehicle.getName()).thenReturn("Enyaq");
        when(location.getLatitude()).thenReturn(44.4);
        when(location.getLongitude()).thenReturn(26.1);
        when(range.getRemainingRangeInKm()).thenReturn(250);
        when(range.getCurrentSoCInPercent()).thenReturn(70);

        CyclicBarrier startedTogether = new CyclicBarrier(4);
        when(service.getVehicles()).thenAnswer(invocation -> afterBarrier(startedTogether, List.of(vehicle)));
        when(service.getVehicleLocation(VIN)).thenAnswer(invocation -> afterBarrier(startedTogether, location));
        when(service.getVehicleStatus(VIN)).thenAnswer(invocation -> afterBarrier(startedTogether, status));
        when(service.getVehicleRange(VIN)).thenAnswer(invocation -> afterBarrier(startedTogether, range));

        VehicleSnapshot snapshot = facade.snapshot();

        assertFalse(snapshot.partial());
        assertEquals("Enyaq", snapshot.vehicleName());
        assertEquals(44.4, snapshot.location().latitude());
        assertEquals(250, snapshot.rangeKm());
        verify(service).getVehicleLocation(VIN);
        verify(service).getVehicleStatus(VIN);
        verify(service).getVehicleRange(VIN);
    }

    @Test
    void warmUpAuthenticatesAndCachesIdentityBeforeRequests() {
        when(service.getVehicles()).thenReturn(List.of());

        facade.warmUp();

        verify(service).authenticate();
        verify(service).getVehicles();
    }

    @Test
    void optionalIdentityFailureDoesNotPreventGatewayWarmUp() {
        when(service.getVehicles()).thenThrow(new RuntimeException("missing optional plate"));

        facade.warmUp();

        verify(service).authenticate();
    }

    @Test
    void returnsAvailableDataWhenOneSectionFails() {
        when(service.getVehicles()).thenReturn(List.of());
        when(service.getVehicleLocation(VIN)).thenThrow(new RuntimeException("unavailable"));
        when(service.getVehicleStatus(VIN)).thenReturn(mock(VehicleStatus.class));
        when(service.getVehicleRange(VIN)).thenReturn(mock(VehicleRange.class));

        VehicleSnapshot snapshot = facade.snapshot();

        assertTrue(snapshot.partial());
        assertEquals(List.of("location"), snapshot.unavailableSections());
    }

    @Test
    void rejectsAResponseWhenEveryCoreSectionFails() {
        when(service.getVehicles()).thenReturn(List.of());
        when(service.getVehicleLocation(anyString())).thenThrow(new RuntimeException("unavailable"));
        when(service.getVehicleStatus(anyString())).thenThrow(new RuntimeException("unavailable"));
        when(service.getVehicleRange(anyString())).thenThrow(new RuntimeException("unavailable"));

        assertThrows(IllegalStateException.class, facade::snapshot);
    }

    @Test
    void totalDeadlineDoesNotWaitForAnUpstreamCallThatIgnoresInterrupts() {
        facade = new SkodaVehicleFacade("email", "password", VIN, "1234", "", 5, 1);
        ReflectionTestUtils.setField(facade, "vehicleService", service);
        when(service.getVehicles()).thenAnswer(invocation -> blockIgnoringInterrupts());
        when(service.getVehicleLocation(anyString())).thenAnswer(invocation -> blockIgnoringInterrupts());
        when(service.getVehicleStatus(anyString())).thenAnswer(invocation -> blockIgnoringInterrupts());
        when(service.getVehicleRange(anyString())).thenAnswer(invocation -> blockIgnoringInterrupts());

        long startedAt = System.nanoTime();
        assertThrows(IllegalStateException.class, facade::snapshot);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsedMillis < 1800, "snapshot exceeded its total deadline: " + elapsedMillis + " ms");
    }

    private static <T> T afterBarrier(CyclicBarrier barrier, T result) throws Exception {
        barrier.await(1, TimeUnit.SECONDS);
        return result;
    }

    private static <T> T blockIgnoringInterrupts() {
        long finishAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < finishAt) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
            Thread.interrupted();
        }
        return null;
    }
}
