package com.gd.skoda.api.client;

import com.gd.skoda.ApiClient;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleServiceVehicleAccessTest {

    private static final String VIN = "QMGAG8BEQSY003476";

    private final List<Request> requests = new ArrayList<>();
    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        Interceptor acceptingServer = chain -> {
            requests.add(chain.request());
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(202)
                    .message("Accepted")
                    .body(ResponseBody.create("", MediaType.get("application/json")))
                    .build();
        };

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(acceptingServer)
                .build();
        ApiClient apiClient = new ApiClient(httpClient).setBasePath("http://localhost");
        vehicleService = new VehicleService(apiClient);
    }

    @Test
    void flashesVehicleLights() throws IOException {
        vehicleService.flashVehicleLights(VIN);

        assertRequest("/vehicle-access/" + VIN + "/flash", "");
    }

    @Test
    void honksAndFlashesVehicle() throws IOException {
        vehicleService.honkAndFlashVehicle(VIN);

        assertRequest("/vehicle-access/" + VIN + "/honk-and-flash", "");
    }

    @Test
    void locksVehicleWithSpin() throws IOException {
        vehicleService.lockVehicle(VIN, "1234");

        assertRequest("/vehicle-access/" + VIN + "/lock", "{\"spin\":\"1234\"}");
    }

    @Test
    void unlocksVehicleWithSpin() throws IOException {
        vehicleService.unlockVehicle(VIN, "4321");

        assertRequest("/vehicle-access/" + VIN + "/unlock", "{\"spin\":\"4321\"}");
    }

    @Test
    void rejectsMalformedSpinBeforeSendingRequest() {
        for (String invalidSpin : new String[]{null, "123", "12345", "12ab"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> vehicleService.unlockVehicle(VIN, invalidSpin));
        }

        assertEquals(0, requests.size());
    }

    private void assertRequest(String expectedPath, String expectedBody) throws IOException {
        assertEquals(1, requests.size());
        Request request = requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals(expectedPath, request.url().encodedPath());

        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        assertEquals(expectedBody, buffer.readUtf8());
    }
}
