package com.gd.skoda.upstream;

import com.gd.skoda.upstream.publicapi.api.VehiclesApi;
import com.gd.skoda.upstream.publicapi.model.VehiclePart;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.service.invoker.RequestParamArgumentResolver;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;

/**
 * Builds the {@link RestClient} and the generated {@code @HttpExchange} proxies for the MyŠkoda
 * Public API. Kept separate from the Spring configuration so tests can bind a
 * {@code MockRestServiceServer} to exactly the same setup.
 */
public final class PublicApiHttpClients {

    private PublicApiHttpClients() {
    }

    /** JDK {@code HttpClient} based request factory using {@code timeout} as connect and read timeout. */
    public static ClientHttpRequestFactory requestFactory(Duration timeout) {
        return ClientHttpRequestFactoryBuilder.jdk()
                .build(HttpClientSettings.defaults().withTimeouts(timeout, timeout));
    }

    /**
     * Applies base URL, API key interceptor and error mapping to {@code builder}. Does not touch the
     * request factory, so the caller decides how requests are sent.
     *
     * <p>URIs are encoded per component ({@link EncodingMode#URI_COMPONENT}) so the comma delimiting
     * {@code include} values stays literal ({@code include=info,status}) as the contract's
     * {@code style: form, explode: false} requires, instead of being sent as {@code %2C}.
     */
    public static RestClient.Builder configure(RestClient.Builder builder, URI baseUrl,
            ApiKeyInterceptor apiKeyInterceptor, PublicApiErrorHandler errorHandler) {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl.toString());
        uriBuilderFactory.setEncodingMode(EncodingMode.URI_COMPONENT);
        return builder
                .uriBuilderFactory(uriBuilderFactory)
                .requestInterceptor(apiKeyInterceptor)
                .defaultStatusHandler(HttpStatusCode::isError, errorHandler);
    }

    /**
     * Creates the generated {@link VehiclesApi} proxy.
     *
     * <p>The contract declares {@code include} as {@code explode: false}, i.e. a single comma-separated
     * value ({@code include=info,status}), using the enum's wire values ({@code fuelStatus}, not
     * {@code FUEL_STATUS}). Spring's defaults would send repeated parameters with {@link Enum#name()},
     * so a {@link RequestParamArgumentResolver} that favours single values and a {@link VehiclePart}
     * converter are registered.
     */
    public static VehiclesApi vehiclesApi(RestClient restClient) {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(VehiclePart.class, String.class, VehiclePart::getValue);
        RequestParamArgumentResolver commaSeparatedParams = new RequestParamArgumentResolver(conversionService);
        commaSeparatedParams.setFavorSingleValue(true);

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .conversionService(conversionService)
                .customArgumentResolver(commaSeparatedParams)
                .build()
                .createClient(VehiclesApi.class);
    }
}
