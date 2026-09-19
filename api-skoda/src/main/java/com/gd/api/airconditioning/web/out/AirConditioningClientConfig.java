package com.gd.api.airconditioning.web.out;

import com.gd.api.core.error.ApiErrorDecoder;
import com.gd.api.core.web.AuthInterceptor;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AirConditioningClientConfig {

    @Bean
    public AirConditioningClient airConditioningClient() {
        return Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .errorDecoder(new ApiErrorDecoder())
                .requestInterceptor(new AuthInterceptor())
                .target(AirConditioningClient.class, "https://mysmob.api.connect.skoda-auto.cz");
    }
}
