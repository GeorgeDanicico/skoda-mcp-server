package com.gd.skoda.mcp;

import com.gd.skoda.api.client.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkodaToolsTest {

    private static final String CONFIGURED_VIN = "TMB00000000000000";

    private RecordingVehicleService vehicleService;
    private SkodaTools tools;

    @BeforeEach
    void setUp() {
        vehicleService = new RecordingVehicleService();
        tools = toolsWith(CONFIGURED_VIN, "1234");
    }

    @Test
    void usesConfiguredVinWhenToolArgumentIsMissing() {
        tools.getVehicleLocation(null);

        assertEquals("location:" + CONFIGURED_VIN, vehicleService.lastCall);
    }

    @Test
    void usesConfiguredVinWhenToolArgumentIsBlank() {
        tools.getVehicleStatus("  ");

        assertEquals("status:" + CONFIGURED_VIN, vehicleService.lastCall);
    }

    @Test
    void explicitVinOverridesConfiguredVin() {
        String overrideVin = "TMB11111111111111";

        tools.getVehicleRange(overrideVin);

        assertEquals("range:" + overrideVin, vehicleService.lastCall);
    }

    @Test
    void vehicleCommandsUseConfiguredVinAndSpin() {
        assertEquals("The vehicle lights were flashed successfully.", tools.flashVehicleLights(null));
        assertEquals("flash:" + CONFIGURED_VIN, vehicleService.lastCall);

        assertEquals("The vehicle horn and lights were activated successfully.", tools.honkAndFlashVehicle(null));
        assertEquals("honk-and-flash:" + CONFIGURED_VIN, vehicleService.lastCall);

        assertEquals("The vehicle was locked successfully.", tools.lockVehicle(null));
        assertEquals("lock:" + CONFIGURED_VIN + ":1234", vehicleService.lastCall);

        assertEquals("The vehicle was unlocked successfully.", tools.unlockVehicle(null));
        assertEquals("unlock:" + CONFIGURED_VIN + ":1234", vehicleService.lastCall);
    }

    @Test
    void missingConfiguredVinFailsBeforeCallingApi() {
        tools = toolsWith("", "1234");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tools.getVehicleLocation(null));

        assertEquals("skoda.vin must be configured before using Skoda tools", exception.getMessage());
        assertNull(vehicleService.lastCall);
    }

    @Test
    void invalidSpinFailsBeforeCallingApi() {
        tools = toolsWith(CONFIGURED_VIN, "12ab");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tools.unlockVehicle(null));

        assertEquals("skoda.spin must contain exactly four digits", exception.getMessage());
        assertNull(vehicleService.lastCall);
    }

    private SkodaTools toolsWith(String vin, String spin) {
        SkodaTools configuredTools = new SkodaTools("email", "password", vin, spin, "");
        ReflectionTestUtils.setField(configuredTools, "vehicleService", vehicleService);
        return configuredTools;
    }

    private static final class RecordingVehicleService extends VehicleService {

        private String lastCall;

        private RecordingVehicleService() {
            super("email", "password");
        }

        @Override
        public com.gd.skoda.api.client.VehicleLocation getVehicleLocation(String vin) {
            lastCall = "location:" + vin;
            return null;
        }

        @Override
        public com.gd.skoda.api.client.VehicleStatus getVehicleStatus(String vin) {
            lastCall = "status:" + vin;
            return null;
        }

        @Override
        public com.gd.skoda.api.client.VehicleRange getVehicleRange(String vin) {
            lastCall = "range:" + vin;
            return null;
        }

        @Override
        public void flashVehicleLights(String vin) {
            lastCall = "flash:" + vin;
        }

        @Override
        public void honkAndFlashVehicle(String vin) {
            lastCall = "honk-and-flash:" + vin;
        }

        @Override
        public void lockVehicle(String vin, String spin) {
            lastCall = "lock:" + vin + ":" + spin;
        }

        @Override
        public void unlockVehicle(String vin, String spin) {
            lastCall = "unlock:" + vin + ":" + spin;
        }
    }
}
