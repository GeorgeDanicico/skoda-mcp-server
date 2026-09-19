package com.gd.api.range.controller;

import com.gd.api.range.resource.out.EngineRangeResponseResource;
import com.gd.api.range.resource.out.RangeResponseResource;
import com.gd.api.range.web.in.RangeWebController;
import com.gd.api.range.web.out.RangeClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(RangeWebController.class)
@ComponentScan(basePackages = {"com.gd.api.range"})
public class RangeWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RangeClient client;

    @Test
    public void getRange() throws Exception {
        RangeResponseResource rangeResponseResource = getRangeResponseResource();
        Mockito.when(client.findRangeByVin("QMGAG8BEQSY003476")).thenReturn(rangeResponseResource);

        String response = """
                {
                  "carType": "electric",
                  "totalRangeInKm": 92,
                  "engineType": "electric",
                  "currentSoCInPercent": 35,
                  "remainingRangeInKm": 92,
                  "carCapturedTimestamp": "2026-02-22T17:25:55.088Z"
                }
                """;

        mockMvc.perform(get("/range/QMGAG8BEQSY003476")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    private static RangeResponseResource getRangeResponseResource() {
        EngineRangeResponseResource engineRangeResponseResource = new EngineRangeResponseResource();
        engineRangeResponseResource.setEngineType("electric");
        engineRangeResponseResource.setCurrentSoCInPercent(35);
        engineRangeResponseResource.setRemainingRangeInKm(92);

        RangeResponseResource rangeResponseResource = new RangeResponseResource();
        rangeResponseResource.setCarType("electric");
        rangeResponseResource.setTotalRangeInKm(92);
        rangeResponseResource.setPrimaryEngineRange(engineRangeResponseResource);
        rangeResponseResource.setCarCapturedTimestamp("2026-02-22T17:25:55.088Z");
        return rangeResponseResource;
    }
}
