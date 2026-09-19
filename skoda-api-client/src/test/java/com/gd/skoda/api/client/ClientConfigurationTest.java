package com.gd.skoda.api.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientConfigurationTest {
    @Test
    void authenticationIsNotCutOffByTheDownstreamCallDeadline() {
        ClientConfiguration configuration = new ClientConfiguration("email", "password", "http://localhost");

        assertEquals(0, configuration.getApiClient().getHttpClient().callTimeoutMillis());
        assertEquals(5_000, configuration.getApiClient().getHttpClient().connectTimeoutMillis());
        assertEquals(15_000, configuration.getApiClient().getHttpClient().readTimeoutMillis());
    }
}
