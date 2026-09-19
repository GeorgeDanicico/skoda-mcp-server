package com.gd.skoda.api.client;

import com.gd.skoda.client.resource.StatusWebResponseResource;
import com.gd.skoda.client.resource.VehicleWebResponseResource;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NullableResponseContractTest {
    @Test
    void acceptsNullableVehicleIdentityFields() {
        assertDoesNotThrow(() -> VehicleWebResponseResource.validateJsonElement(JsonParser.parseString("""
                {"vin":"TESTVIN","name":null,"licensePlate":null,"state":null,
                 "devicePlatform":null,"systemModelId":null,"title":null}
                """)));
    }

    @Test
    void acceptsNullableStatusFields() {
        assertDoesNotThrow(() -> StatusWebResponseResource.validateJsonElement(JsonParser.parseString("""
                {"doorsLocked":null,"locked":null,"doors":"CLOSED","windows":"CLOSED",
                 "lights":null,"reliableLockStatus":null,"sunroof":null,"trunk":"CLOSED",
                 "bonnet":"CLOSED","carCapturedTimestamp":null}
                """)));
    }
}
