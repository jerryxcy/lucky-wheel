package io.github.jerryxcy.luckywheel.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice(basePackageClasses = SharedWheelController.class)
class SharedWheelProblemDetails {

    private static final Logger log = LoggerFactory.getLogger(SharedWheelProblemDetails.class);

    private static final String PROBLEM_BASE =
            "https://github.com/jerryxcy/lucky-wheel/problems/";

    @ExceptionHandler(SharedWheelNotFoundException.class)
    ResponseEntity<ProblemDetail> sharedWheelNotFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Shared Wheel was not found."
        );
        problem.setType(URI.create(PROBLEM_BASE + "shared-wheel-not-found"));
        problem.setTitle("Shared Wheel not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    @ExceptionHandler(SharedWheelValidationException.class)
    ResponseEntity<ProblemDetail> sharedWheelValidation(SharedWheelValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Shared Wheel validation failed."
        );
        problem.setType(URI.create(PROBLEM_BASE + "shared-wheel-validation"));
        problem.setTitle("Invalid Shared Wheel");
        problem.setProperty("errors", exception.errors());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ProblemDetail> invalidSharedWheelRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The Shared Wheel request is malformed or contains an invalid value."
        );
        problem.setType(URI.create(PROBLEM_BASE + "shared-wheel-invalid-request"));
        problem.setTitle("Invalid Shared Wheel request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpectedSharedApiFailure(Exception exception) {
        log.error("Unexpected Shared API failure", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The Shared Wheel request could not be completed."
        );
        problem.setType(URI.create(PROBLEM_BASE + "shared-api-error"));
        problem.setTitle("Shared API failure");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
