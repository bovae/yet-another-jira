package com.bovae.yaj.web.error;

import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.ForbiddenException;
import com.bovae.yaj.error.GoneException;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.RateLimitException;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.error.ValidationException;
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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
        return domainProblem(HttpStatus.NOT_FOUND, "Not Found", ex, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex, WebRequest request) {
        return domainProblem(HttpStatus.CONFLICT, "Conflict", ex, request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidation(ValidationException ex, WebRequest request) {
        return domainProblem(HttpStatus.BAD_REQUEST, "Validation Failed", ex, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        return domainProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException ex, WebRequest request) {
        return domainProblem(HttpStatus.FORBIDDEN, "Forbidden", ex, request);
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<Object> handleGone(GoneException ex, WebRequest request) {
        return domainProblem(HttpStatus.GONE, "Gone", ex, request);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Object> handleRateLimit(RateLimitException ex, WebRequest request) {
        ProblemDetail body =
                ProblemDetailFactory.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()));
        return handleExceptionInternal(ex, body, headers, HttpStatus.TOO_MANY_REQUESTS, request);
    }

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

    private ResponseEntity<Object> domainProblem(
            HttpStatus status, String title, RuntimeException ex, WebRequest request) {
        ProblemDetail body = ProblemDetailFactory.create(status, title, ex.getMessage());
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
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
