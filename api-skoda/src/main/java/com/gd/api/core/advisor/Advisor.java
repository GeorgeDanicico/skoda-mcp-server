package com.gd.api.core.advisor;

import com.gd.api.core.exception.ApiException;
import com.gd.api.resource.ProblemDetailResponseResource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class Advisor {

    @ExceptionHandler({ApiException.class})
    public final ResponseEntity<ProblemDetailResponseResource> handleException(ApiException ex, HttpServletRequest request) {
        log.error("error code: {}, message: {}", ex.getErrorCode(), ex.getErrorMessage());
        ProblemDetailResponseResource problemDetailResponseResource = ProblemDetailResponseResource.builder()
                .title("Something went wrong")
                .status(500)
                .detail(String.format("Code: %s, Message: %s", ex.getErrorCode(), ex.getErrorMessage()))
                .instance(request.getRequestURI())
                .build();

        return ResponseEntity.internalServerError().body(problemDetailResponseResource);
    }
}
