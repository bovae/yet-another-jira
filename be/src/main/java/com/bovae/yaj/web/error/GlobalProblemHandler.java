package com.bovae.yaj.web.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized RFC 9457 problem-details handler for the API. Extends {@link
 * ResponseEntityExceptionHandler} so framework exceptions (404, validation, etc.) are emitted as
 * {@code application/problem+json}, enriched with {@code correlationId} and {@code timestamp} via
 * {@link ProblemDetailFactory}. A catch-all maps any other exception to a generic 500 that leaks no
 * internals.
 */
@Slf4j
@RestControllerAdvice
public class GlobalProblemHandler extends ResponseEntityExceptionHandler {

    /**
     * Maps any otherwise-unhandled exception to a generic 500 that excludes internals from the body;
     * the cause is recorded only in the correlated server log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        LOG.error("Unhandled exception while processing request", ex);
        ProblemDetail body =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        body.setTitle("Internal Server Error");
        // Common members (correlationId, timestamp) are applied once, centrally, in handleExceptionInternal.
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Single enrichment point: adds the common members to every problem body and titles validation failures. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problemDetail =
                (body instanceof ProblemDetail existing) ? existing : ProblemDetail.forStatus(statusCode);
        if (problemDetail.getTitle() == null) {
            problemDetail.setTitle(reasonPhrase(statusCode));
        }

        // The base handler supplies a null body for MethodArgumentNotValidException, so the
        // validation title is applied here regardless of which branch produced the ProblemDetail.
        if (ex instanceof MethodArgumentNotValidException) {
            problemDetail.setTitle("Validation Failed");
        }
        ProblemDetailFactory.applyCommonMembers(problemDetail);
        return super.handleExceptionInternal(ex, problemDetail, headers, statusCode, request);
    }

    /** Human-readable reason phrase for the status, or {@code "Error"} for a non-standard code. */
    private static String reasonPhrase(HttpStatusCode statusCode) {
        return HttpStatus.resolve(statusCode.value()) != null
                ? HttpStatus.valueOf(statusCode.value()).getReasonPhrase()
                : "Error";
    }
}
