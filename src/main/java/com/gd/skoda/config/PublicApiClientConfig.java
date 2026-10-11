package com.gd.skoda.config;

import com.gd.skoda.upstream.ApiKeyExpiryWarning;
import com.gd.skoda.upstream.ApiKeyInterceptor;
import com.gd.skoda.upstream.ApiKeyStatus;
import com.gd.skoda.upstream.PublicApiClient;
import com.gd.skoda.upstream.PublicApiErrorHandler;
import com.gd.skoda.upstream.PublicApiHttpClients;
import com.gd.skoda.upstream.publicapi.api.VehiclesApi;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the MyŠkoda Public API client: a dedicated {@link RestClient} (base URL and timeouts from
 * {@link SkodaProperties#publicApi()}, {@code X-API-Key} interceptor, problem+json error mapping) and
 * the generated {@link VehiclesApi} HTTP interface proxy behind the {@link PublicApiClient} facade.
 *
 * <p>Starts from Boot's auto-configured {@link RestClient.Builder} so the application's Jackson 3
 * {@link JsonMapper} and message converters are used.
 */
@Configuration(proxyBeanMethods = false)
public class PublicApiClientConfig {

    @Bean
    ApiKeyStatus apiKeyStatus(Clock clock) {
        return new ApiKeyStatus(clock);
    }

    @Bean
    ApiKeyExpiryWarning apiKeyExpiryWarning(Clock clock) {
        return new ApiKeyExpiryWarning(clock);
    }

    @Bean
    ApiKeyInterceptor apiKeyInterceptor(SkodaProperties properties, ApiKeyStatus apiKeyStatus,
            ApiKeyExpiryWarning apiKeyExpiryWarning) {
        return new ApiKeyInterceptor(properties.publicApi().key(), apiKeyStatus, apiKeyExpiryWarning);
    }

    @Bean
    PublicApiErrorHandler publicApiErrorHandler(JsonMapper jsonMapper, Clock clock) {
        return new PublicApiErrorHandler(jsonMapper, clock);
    }

    @Bean
    RestClient skodaPublicApiRestClient(RestClient.Builder restClientBuilder, SkodaProperties properties,
            ApiKeyInterceptor apiKeyInterceptor, PublicApiErrorHandler publicApiErrorHandler) {
        SkodaProperties.PublicApi publicApi = properties.publicApi();
        RestClient.Builder builder = restClientBuilder
                .requestFactory(PublicApiHttpClients.requestFactory(publicApi.timeout()));
        return PublicApiHttpClients.configure(builder, publicApi.baseUrl(), apiKeyInterceptor, publicApiErrorHandler)
                .build();
    }

    @Bean
    VehiclesApi vehiclesApi(RestClient skodaPublicApiRestClient) {
        return PublicApiHttpClients.vehiclesApi(skodaPublicApiRestClient);
    }

    @Bean
    PublicApiClient publicApiClient(VehiclesApi vehiclesApi, SkodaProperties properties) {
        return new PublicApiClient(vehiclesApi, properties.vin());
    }
}
