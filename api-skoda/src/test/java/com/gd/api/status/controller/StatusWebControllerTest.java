package com.gd.api.status.controller;

import com.gd.api.status.resource.out.*;
import com.gd.api.status.web.in.StatusWebController;
import com.gd.api.status.web.out.StatusClient;
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

@WebMvcTest(StatusWebController.class)
@ComponentScan(basePackages = {"com.gd.api.status"})
public class StatusWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusClient client;

    @Test
    public void getStatus() throws Exception {
        StatusResponseResource statusResponseResource = getStatusResponseResource();
        Mockito.when(client.getStatus("QMGAG8BEQSY003476")).thenReturn(statusResponseResource);

        String response = """
                {
                     "doorsLocked":"YES",
                     "locked":"YES",
                     "doors":"CLOSED",
                     "windows":"CLOSED",
                     "lights":"OFF",
                     "reliableLockStatus":"LOCKED",
                     "sunroof":"UNSUPPORTED",
                     "trunk":"CLOSED",
                     "bonnet":"CLOSED",
                     "carCapturedTimestamp":"2026-02-22T12:26:45.258Z"
                }
                """;

        mockMvc.perform(get("/status/QMGAG8BEQSY003476")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().json(response, STRICT));
    }

    private static StatusResponseResource getStatusResponseResource() {
        StatusOverallResponseResource overall = new StatusOverallResponseResource();
        overall.setDoorsLocked("YES");
        overall.setLocked("YES");
        overall.setDoors("CLOSED");
        overall.setWindows("CLOSED");
        overall.setLights("OFF");
        overall.setReliableLockStatus("LOCKED");

        StatusDetailResponseResource detail = new StatusDetailResponseResource();
        detail.setSunroof("UNSUPPORTED");
        detail.setTrunk("CLOSED");
        detail.setBonnet("CLOSED");

        StatusResponseResource statusResponseResource = new StatusResponseResource();
        statusResponseResource.setOverall(overall);
        statusResponseResource.setDetail(detail);
        statusResponseResource.setCarCapturedTimestamp("2026-02-22T12:26:45.258Z");
        return statusResponseResource;
    }
}
