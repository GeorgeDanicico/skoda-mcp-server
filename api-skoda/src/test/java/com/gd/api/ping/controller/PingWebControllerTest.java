package com.gd.api.ping.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PingWebController.class)
@ComponentScan(basePackages = {"com.gd.api.ping"})
public class PingWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void ping() throws Exception {
        this.mockMvc.perform(get("/ping")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
