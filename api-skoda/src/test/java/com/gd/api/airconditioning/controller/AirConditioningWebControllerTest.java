package com.gd.api.airconditioning.controller;

import com.gd.api.airconditioning.resource.out.AirConditioningResponseResource;
import com.gd.api.airconditioning.resource.out.AirConditioningStartRequestResource;
import com.gd.api.airconditioning.resource.out.AirConditioningTargetTemperatureResponseResource;
import com.gd.api.airconditioning.web.in.AirConditioningWebController;
import com.gd.api.airconditioning.web.out.AirConditioningClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AirConditioningWebController.class)
@ComponentScan(basePackages = {"com.gd.api.airconditioning"})
public class AirConditioningWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AirConditioningClient client;

    private static AirConditioningResponseResource getAirConditioningResponseResource() {
        AirConditioningTargetTemperatureResponseResource targetTemperature = new AirConditioningTargetTemperatureResponseResource();
        targetTemperature.setTemperatureValue(21.0);
        targetTemperature.setUnitInCar("CELSIUS");

        AirConditioningResponseResource airConditioningResponseResource = new AirConditioningResponseResource();
        airConditioningResponseResource.setState("OFF");
        airConditioningResponseResource.setTargetTemperature(targetTemperature);
        airConditioningResponseResource.setCarCapturedTimestamp("2026-02-22T17:25:55.088Z");
        return airConditioningResponseResource;
    }

    @Test
    public void getAirConditioning() throws Exception {
        AirConditioningResponseResource airConditioningResponseResource = getAirConditioningResponseResource();
        Mockito.when(client.findAirConditioningByVin("QMGAG8BEQSY003476")).thenReturn(airConditioningResponseResource);

        String response = """
                {
                  "state": "OFF",
                  "temperature": 21.0,
                  "temperatureUnit": "CELSIUS",
                  "carCapturedTimestamp": "2026-02-22T17:25:55.088Z"
                }
                """;

        mockMvc.perform(get("/air-conditioning/QMGAG8BEQSY003476")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    @Test
    public void startAirConditioning() throws Exception {
        String request = """
                {
                  "heaterSource": "ELECTRIC",
                  "temperature": 21.5,
                  "temperatureUnit": "CELSIUS"
                }
                """;

        mockMvc.perform(post("/air-conditioning/QMGAG8BEQSY003476/start")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted());

        ArgumentCaptor<AirConditioningStartRequestResource> argumentCaptor = ArgumentCaptor.forClass(AirConditioningStartRequestResource.class);
        verify(client).startAirConditioningByVin(eq("QMGAG8BEQSY003476"), argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getHeaterSource()).isEqualTo("ELECTRIC");
        assertThat(argumentCaptor.getValue().getTargetTemperature().getTemperatureValue()).isEqualTo(21.5);
        assertThat(argumentCaptor.getValue().getTargetTemperature().getUnitInCar()).isEqualTo("CELSIUS");
    }

    @Test
    public void stopAirConditioning() throws Exception {
        mockMvc.perform(post("/air-conditioning/QMGAG8BEQSY003476/stop")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        verify(client).stopAirConditioningByVin("QMGAG8BEQSY003476");
    }
}
