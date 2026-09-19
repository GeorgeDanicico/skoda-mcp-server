package com.gd.skoda.api.client;

import com.gd.skoda.ApiClient;
import com.gd.skoda.ServerConfiguration;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClientConfiguration {

    private final ApiClient apiClient;
    private final TokenService tokenService;

    ClientConfiguration(String email, String password) {
        this.tokenService = new TokenService(email, password);

        Interceptor authorizationInterceptor = chain -> {
            Request originalRequest = chain.request();
            Request requestWithAuthorization = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + tokenService.getToken())
                    .build();
            return chain.proceed(requestWithAuthorization);
        };

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(authorizationInterceptor)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        this.apiClient = new ApiClient(httpClient);
    }

    ClientConfiguration(String email, String password, String server) {
        this.tokenService = new TokenService(email, password);

        Interceptor authorizationInterceptor = chain -> {
            Request originalRequest = chain.request();
            Request requestWithAuthorization = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + tokenService.getToken())
                    .build();
            return chain.proceed(requestWithAuthorization);
        };

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(authorizationInterceptor)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        ServerConfiguration serverConfiguration = new ServerConfiguration(
                server,
                "No description provided",
                new HashMap<>()
        );
        this.apiClient = new ApiClient(httpClient);
        this.apiClient.setBasePath(server);
        this.apiClient.setServers(List.of(serverConfiguration));
    }

    ApiClient getApiClient() {
        return this.apiClient;
    }

    void authenticate() {
        tokenService.getToken();
    }
}
