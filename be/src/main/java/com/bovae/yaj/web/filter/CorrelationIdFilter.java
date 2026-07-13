package com.bovae.yaj.web.filter;

import com.bovae.yaj.support.CorrelationId;
import com.bovae.yaj.support.UUIDUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the per-request correlation id: uses the client {@code X-Correlation-Id} header when
 * it is a valid UUID (rejecting non-UUID values blocks log forging), else generates one. Puts it in
 * the MDC and echoes it on the response header, including error paths.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(CorrelationId.HEADER));

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);

        chain.doFilter(request, response);
    }

    private static String resolveCorrelationId(String headerValue) {
        if (headerValue != null) {
            String trimmed = headerValue.trim();
            if (UUIDUtils.isValidUUID(trimmed)) {
                return trimmed;
            }
        }
        return UUIDUtils.getRandomUUID();
    }
}
