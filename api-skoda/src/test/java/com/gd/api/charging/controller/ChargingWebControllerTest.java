package com.gd.api.charging.controller;

import com.gd.api.charging.resource.out.ChargingHistoryPeriodResponseResource;
import com.gd.api.charging.resource.out.ChargingHistoryResponseResource;
import com.gd.api.charging.resource.out.ChargingHistorySessionResponseResource;
import com.gd.api.charging.resource.out.ChargingResponseResource;
import com.gd.api.charging.resource.out.ChargingSettingsResponseResource;
import com.gd.api.charging.resource.out.ChargingStatusBatteryResponseResource;
import com.gd.api.charging.resource.out.ChargingStatusResponseResource;
import com.gd.api.charging.web.in.ChargingWebController;
import com.gd.api.charging.web.out.ChargingClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChargingWebController.class)
@ComponentScan(basePackages = {"com.gd.api.charging"})
public class ChargingWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChargingClient client;

    private static ChargingResponseResource getChargingResponseResource() {
        ChargingStatusBatteryResponseResource battery = new ChargingStatusBatteryResponseResource();
        battery.setRemainingCruisingRangeInMeters(126000);
        battery.setStateOfChargeInPercent(48);

        ChargingStatusResponseResource status = new ChargingStatusResponseResource();
        status.setChargingRateInKilometersPerHour(15.0);
        status.setChargePowerInKw(3.5);
        status.setRemainingTimeToFullyChargedInMinutes(570);
        status.setState("CHARGING");
        status.setFullyChargedAt("2026-02-24T03:50:31Z");
        status.setChargeType("AC");
        status.setBattery(battery);

        ChargingSettingsResponseResource settings = new ChargingSettingsResponseResource();
        settings.setTargetStateOfChargeInPercent(100);
        settings.setBatteryCareModeTargetValueInPercent(80);
        settings.setPreferredChargeMode("MANUAL");
        settings.setAvailableChargeModes(List.of("MANUAL"));
        settings.setChargingCareMode("DEACTIVATED");
        settings.setAutoUnlockPlugWhenCharged("OFF");
        settings.setMaxChargeCurrentAc("MAXIMUM");

        ChargingResponseResource chargingResponseResource = new ChargingResponseResource();
        chargingResponseResource.setVehicleInSavedLocation(true);
        chargingResponseResource.setStatus(status);
        chargingResponseResource.setSettings(settings);
        chargingResponseResource.setCarCapturedTimestamp("2026-02-22T17:25:55.088Z");
        return chargingResponseResource;
    }

    private static ChargingHistoryResponseResource getChargingHistoryResponseResource() {
        ChargingHistorySessionResponseResource session1 = new ChargingHistorySessionResponseResource();
        session1.setStartAt("2026-02-22T11:15:25Z");
        session1.setChargedInKWh(23.0);
        session1.setDurationInMinutes(714);
        session1.setCurrentType("AC");

        ChargingHistorySessionResponseResource session2 = new ChargingHistorySessionResponseResource();
        session2.setStartAt("2026-02-22T08:50:56Z");
        session2.setChargedInKWh(7.0);
        session2.setDurationInMinutes(92);
        session2.setCurrentType("AC");

        ChargingHistoryPeriodResponseResource periods = new ChargingHistoryPeriodResponseResource();
        periods.setTotalChargedInKWh(214.0);
        periods.setSessions(List.of(session1, session2));

        ChargingHistoryResponseResource history = new ChargingHistoryResponseResource();
        history.setNextCursor("2026-02-14T14:27:04Z");
        history.setPeriods(periods);

        return history;
    }

    @Test
    void getChargingState() throws Exception {
        ChargingResponseResource chargingResponseResource = getChargingResponseResource();
        Mockito.when(client.findChargingByVin("QMGAG8BEQSY003476")).thenReturn(chargingResponseResource);

        String response = """
                {
                  "chargingRateInKilometersPerHour": 15.0,
                  "chargePowerInKw": 3.5,
                  "remainingTimeToFullyChargedInMinutes": 570,
                  "state": "CHARGING",
                  "chargeType": "AC",
                  "remainingCruisingRangeInMeters": 126000,
                  "stateOfChargeInPercent": 48,
                  "carCapturedTimestamp": "2026-02-22T17:25:55.088Z"
                }
                """;

        mockMvc.perform(get("/charging/QMGAG8BEQSY003476")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    @Test
    void getChargingSessions() throws Exception {
        ChargingHistoryResponseResource chargingHistoryResponseResource = getChargingHistoryResponseResource();
        Mockito.when(client.findChargingHistoryByVin("QMGAG8BEQSY003476")).thenReturn(chargingHistoryResponseResource);

        String response = """
                [
                  {
                    "startAt": "2026-02-22T11:15:25Z",
                    "chargedInKWh": 23.0,
                    "durationInMinutes": 714,
                    "currentType": "AC"
                  },
                  {
                    "startAt": "2026-02-22T08:50:56Z",
                    "chargedInKWh": 7.0,
                    "durationInMinutes": 92,
                    "currentType": "AC"
                  }
                ]
                """;

        mockMvc.perform(get("/charging/QMGAG8BEQSY003476/session")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    @Test
    void startCharging() throws Exception {
        mockMvc.perform(post("/charging/QMGAG8BEQSY003476/start")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        verify(client).startChargingByVin("QMGAG8BEQSY003476");
    }

    @Test
    void stopCharging() throws Exception {
        mockMvc.perform(post("/charging/QMGAG8BEQSY003476/stop")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        verify(client).stopChargingByVin("QMGAG8BEQSY003476");
    }
}
