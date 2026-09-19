package com.gd.api.core.error;

import com.gd.api.core.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;

public class ApiErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    public ApiErrorDecoder() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            if (response.body() != null) {
                ErrorResponse errorResponse = objectMapper.readValue(response.body().asInputStream(), ErrorResponse.class);
                return new ApiException(errorResponse.getError().getErrorCode(), errorResponse.getError().getDescription());
            }
            return new ApiException(response.status() + "", response.headers().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
