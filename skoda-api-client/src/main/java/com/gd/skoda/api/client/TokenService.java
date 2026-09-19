package com.gd.skoda.api.client;

import be.nicholasmeyers.skodaconnector.resource.Tokens;
import be.nicholasmeyers.skodaconnector.service.ConnectorService;

public class TokenService {

    private final ConnectorService connectorService;
    private final String email;
    private final String password;

    TokenService(String email, String password) {
        this.connectorService = new ConnectorService();
        this.email = email;
        this.password = password;
    }

    /**
     * Snapshot sections run concurrently. Serializing token acquisition lets
     * ConnectorService reuse an existing token and prevents a refresh stampede.
     */
    synchronized String getToken() {
        Tokens tokens = connectorService.getTokens(email, password);
        return tokens.getAccessToken();
    }
}
