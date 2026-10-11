package com.gd.skoda.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gd.skoda.config.ServiceApiConfig;
import com.gd.skoda.config.SkodaPropertiesConfiguration;
import com.gd.skoda.error.ApiExceptionHandler;
import com.gd.skoda.vehicle.ApiKeyInfo;
import com.gd.skoda.vehicle.Location;
import com.gd.skoda.vehicle.ParkingState;
import com.gd.skoda.vehicle.VehicleData;
import com.gd.skoda.vehicle.VehicleService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** The generated REST interfaces as implemented by the controllers: JSON shape, no authentication. */
@WebMvcTest(controllers = {VehicleApiController.class, SystemApiController.class},
        properties = {"SKODA_VIN=TMBJJ7NE8L0123456", "SKODA_API_KEY=test-key"})
@Import({SkodaPropertiesConfiguration.class, ServiceApiConfig.class,
        ApiExceptionHandler.class})
@ActiveProfiles("api")
@DisabledInAotMode("@MockitoBean: Mockito cannot create mocks in a native image")
class ApiControllersWebMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VehicleService vehicleService;

    @Nested
    class Endpoints {

        @Test
        void overviewIncludesFreshnessAndExplicitNulls() throws Exception {
            when(vehicleService.getOverview()).thenReturn(ApiTestData.fullOverview());

            mvc.perform(get("/api/v1/vehicle"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(content().json("""
                            {
                              "data": {
                                "name": "Octavia",
                                "licensePlate": "1AB 2345",
                                "location": {
                                  "state": "PARKED",
                                  "latitude": 50.4113,
                                  "longitude": 14.9083,
                                  "formattedAddress": "tř. Václava Klementa 869, 293 01 Mladá Boleslav, CZ",
                                  "capturedAt": null
                                },
                                "range": {
                                  "carType": "DIESEL",
                                  "totalRangeKm": 640,
                                  "primaryEngine": {
                                    "engineType": "DIESEL",
                                    "fuelLevelPercent": 55,
                                    "socPercent": null,
                                    "remainingRangeKm": 640
                                  },
                                  "secondaryEngine": null,
                                  "adBlueRangeKm": 4000,
                                  "batterySocPercent": null,
                                  "capturedAt": "2026-10-11T09:30:00Z"
                                },
                                "status": {
                                  "locked": "YES",
                                  "doorsLocked": "YES",
                                  "doors": "CLOSED",
                                  "windows": "CLOSED",
                                  "lights": "OFF",
                                  "reliableLockStatus": "LOCKED",
                                  "trunk": "CLOSED",
                                  "bonnet": "CLOSED",
                                  "sunroof": "UNSUPPORTED",
                                  "odometerKm": 48210,
                                  "capturedAt": "2026-10-11T09:29:00Z"
                                },
                                "capturedAt": "2026-10-11T09:30:00Z"
                              },
                              "freshness": {
                                "fetchedAt": "2026-10-11T10:00:00Z",
                                "stale": false,
                                "unavailable": [
                                  {
                                    "part": "CHARGING",
                                    "reason": "UNSUPPORTED",
                                    "type": "CHARGING_UNSUPPORTED",
                                    "description": "Charging is not supported"
                                  }
                                ]
                              }
                            }
                            """, JsonCompareMode.STRICT));
        }

        @Test
        void locationInMotion() throws Exception {
            when(vehicleService.getLocation()).thenReturn(new VehicleData<>(
                    new Location(ParkingState.IN_MOTION, null, null, null, null), ApiTestData.freshness(true)));

            mvc.perform(get("/api/v1/vehicle/location"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {
                              "data": {"state": "IN_MOTION", "latitude": null, "longitude": null,
                                       "formattedAddress": null, "capturedAt": null},
                              "freshness": {"fetchedAt": "2026-10-11T10:00:00Z", "stale": true, "unavailable": []}
                            }
                            """, JsonCompareMode.STRICT));
        }

        @Test
        void vehicleRange() throws Exception {
            when(vehicleService.getRange()).thenReturn(new VehicleData<>(ApiTestData.RANGE, ApiTestData.freshness(false)));

            mvc.perform(get("/api/v1/vehicle/range"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalRangeKm").value(640))
                    .andExpect(jsonPath("$.data.primaryEngine.fuelLevelPercent").value(55))
                    .andExpect(jsonPath("$.data.adBlueRangeKm").value(4000))
                    .andExpect(jsonPath("$.freshness.stale").value(false))
                    .andExpect(jsonPath("$.freshness.unavailable").isEmpty());
        }

        @Test
        void vehicleStatus() throws Exception {
            when(vehicleService.getStatus()).thenReturn(new VehicleData<>(ApiTestData.STATUS,
                    ApiTestData.freshness(false, ApiTestData.CHARGING_UNSUPPORTED)));

            mvc.perform(get("/api/v1/vehicle/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.locked").value("YES"))
                    .andExpect(jsonPath("$.data.sunroof").value("UNSUPPORTED"))
                    .andExpect(jsonPath("$.data.odometerKm").value(48210))
                    .andExpect(jsonPath("$.freshness.fetchedAt").value("2026-10-11T10:00:00Z"))
                    .andExpect(jsonPath("$.freshness.unavailable[0].type").value("CHARGING_UNSUPPORTED"));
        }

        @Test
        void apiKeyStatus() throws Exception {
            when(vehicleService.getApiKeyInfo()).thenReturn(ApiTestData.apiKeyInfo());

            mvc.perform(get("/api/v1/system/api-key"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {
                              "keyExpiresAt": "2026-12-31T00:00:00Z",
                              "daysUntilExpiry": 80,
                              "rateLimitLimit": 20,
                              "rateLimitRemaining": 17,
                              "rateLimitResetAt": "2026-10-11T10:45:00Z",
                              "lastUpstreamCallAt": "2026-10-11T10:00:00Z"
                            }
                            """, JsonCompareMode.STRICT));
        }

        @Test
        void apiKeyStatusBeforeFirstUpstreamCallIsAllNull() throws Exception {
            when(vehicleService.getApiKeyInfo()).thenReturn(new ApiKeyInfo(null, null, null, null, null, null));

            mvc.perform(get("/api/v1/system/api-key"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"keyExpiresAt": null, "daysUntilExpiry": null, "rateLimitLimit": null,
                             "rateLimitRemaining": null, "rateLimitResetAt": null, "lastUpstreamCallAt": null}
                            """, JsonCompareMode.STRICT));
        }
    }

    @Nested
    class NoAuthentication {

        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/vehicle", "/api/v1/vehicle/location", "/api/v1/vehicle/range",
                "/api/v1/vehicle/status", "/api/v1/system/api-key"})
        void endpointsNeedNoCredentials(String path) throws Exception {
            stubAllViews();

            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        }

        @Test
        void authorizationHeaderIsIgnored() throws Exception {
            when(vehicleService.getApiKeyInfo()).thenReturn(ApiTestData.apiKeyInfo());

            mvc.perform(get("/api/v1/system/api-key").header(HttpHeaders.AUTHORIZATION, "Bearer anything"))
                    .andExpect(status().isOk());
        }

        private void stubAllViews() {
            when(vehicleService.getOverview()).thenReturn(ApiTestData.fullOverview());
            when(vehicleService.getLocation()).thenReturn(new VehicleData<>(
                    new Location(ParkingState.IN_MOTION, null, null, null, null), ApiTestData.freshness(false)));
            when(vehicleService.getRange()).thenReturn(new VehicleData<>(ApiTestData.RANGE, ApiTestData.freshness(false)));
            when(vehicleService.getStatus()).thenReturn(new VehicleData<>(ApiTestData.STATUS, ApiTestData.freshness(false)));
            when(vehicleService.getApiKeyInfo()).thenReturn(ApiTestData.apiKeyInfo());
        }
    }
}
