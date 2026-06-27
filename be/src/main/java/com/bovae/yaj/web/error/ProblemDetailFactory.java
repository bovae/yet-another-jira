package com.bovae.yaj.web.error;

import com.bovae.yaj.support.CorrelationId;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;

/**
 * Builds and enriches RFC 9457 {@link ProblemDetail} bodies with a {@code correlationId} (read from
 * the MDC, falling back to a placeholder) and a UTC {@code timestamp} member.
 */
public final class ProblemDetailFactory {

    static final String UNKNOWN_CORRELATION_ID = "unknown";

    private ProblemDetailFactory() {
        throw new AssertionError("No com.bovae.yaj.web.error.ProblemDetailFactory instances for you!");
    }

    /** Creates an enriched problem detail; {@code detail} is omitted from the body when {@code null}. */
    public static ProblemDetail create(HttpStatusCode status, String title, @Nullable String detail) {
        ProblemDetail problemDetail =
                detail != null ? ProblemDetail.forStatusAndDetail(status, detail) : ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        applyCommonMembers(problemDetail);
        return problemDetail;
    }

    /** Adds the {@code correlationId} and UTC {@code timestamp} members to an existing problem detail. */
    public static void applyCommonMembers(ProblemDetail problemDetail) {
        problemDetail.setProperty("correlationId", currentCorrelationId());
        problemDetail.setProperty("timestamp", Instant.now().toString());
    }

    private static String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        return correlationId != null && !correlationId.isBlank() ? correlationId : UNKNOWN_CORRELATION_ID;
    }
}
