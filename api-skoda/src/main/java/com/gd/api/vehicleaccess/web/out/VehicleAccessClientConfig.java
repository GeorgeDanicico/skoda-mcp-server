package com.gd.api.vehicleaccess.web.out;

import com.gd.api.core.error.ApiErrorDecoder;
import com.gd.api.core.web.AuthInterceptor;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VehicleAccessClientConfig {

    @Bean
    public VehicleAccessClient vehicleAccessClient() {
        return Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .errorDecoder(new ApiErrorDecoder())
                .requestInterceptor(new AuthInterceptor())
                .target(VehicleAccessClient.class, "https://mysmob.api.connect.skoda-auto.cz");
    }
}
