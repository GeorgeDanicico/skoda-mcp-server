package com.gd.skoda.error;

import com.gd.skoda.upstream.SkodaApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions from the REST controllers into RFC 9457 problem responses ({@code api} profile).
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions (405, 406, ...) keep
 * their standard problem details; Boot's equivalent handler ({@code spring.mvc.problemdetails.enabled})
 * backs off because this bean exists. {@link SkodaApiException}s are mapped by {@link SkodaApiProblems};
 * anything else is a 500 without internal details.
 */
@RestControllerAdvice
@Profile("api")
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final SkodaApiProblems problems;

    public ApiExceptionHandler(SkodaApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(SkodaApiException.class)
    ResponseEntity<ProblemDetail> handleSkodaApiException(SkodaApiException ex) {
        ProblemDetail problem = problems.toProblem(ex);
        log.warn("Request failed with {}: {}", problem.getStatus(), problem.getDetail());
        HttpHeaders headers = new HttpHeaders();
        SkodaApiProblems.retryAfterSeconds(problem)
                .ifPresent(seconds -> headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds)));
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling a request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred in the server. Details are in the server log.");
        problem.setType(ProblemTypes.INTERNAL_ERROR);
        problem.setTitle("Internal server error");
        return ResponseEntity.internalServerError().body(problem);
    }
}
