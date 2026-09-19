package com.gd.api.status.web.out;

import com.gd.api.core.error.ApiErrorDecoder;
import com.gd.api.core.web.AuthInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatusClientConfig {

    @Bean
    public StatusClient statusClient() {
        return Feign.builder()
                .encoder(new JacksonEncoder(objectMapper()))
                .decoder(new JacksonDecoder(objectMapper()))
                .errorDecoder(new ApiErrorDecoder())
                .requestInterceptor(new AuthInterceptor())
                .target(StatusClient.class, "https://mysmob.api.connect.skoda-auto.cz");
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }
}
