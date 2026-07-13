package com.bovae.yaj.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@ExtendWith(MockitoExtension.class)
class MdcCleanupFilterTest {

    private final MdcCleanupFilter filter = new MdcCleanupFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    @AfterEach
    void resetMdc() {
        MDC.clear();
    }

    @Test
    void doFilterInternal_shouldClearAllMdcKeys_whenDownstreamPopulatedThem() throws Exception {
        FilterChain chain = (req, res) -> {
            MDC.put("correlationId", "abc-123");
            MDC.put("userId", "user-42");
        };

        filter.doFilterInternal(request, response, chain);

        assertMdcEmpty();
    }

    @Test
    void doFilterInternal_shouldClearAllMdcKeys_whenDownstreamThrows() {
        FilterChain chain = (req, res) -> {
            MDC.put("correlationId", "abc-123");
            throw new ServletException("downstream failure");
        };

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));

        assertMdcEmpty();
    }

    @Test
    void doFilterInternal_shouldCompleteWithoutError_whenMdcAlreadyEmpty() throws Exception {
        FilterChain chain = (req, res) -> {
            /* no MDC producer ran */
        };

        filter.doFilterInternal(request, response, chain);

        assertMdcEmpty();
    }

    @Test
    void mdcCleanupFilter_shouldRunAtHighestPrecedence() {
        Order order = MdcCleanupFilter.class.getAnnotation(Order.class);

        assertNotNull(order, "MdcCleanupFilter must declare an @Order so it is the outermost filter");
        assertEquals(
                Ordered.HIGHEST_PRECEDENCE,
                order.value(),
                "MdcCleanupFilter must run at HIGHEST_PRECEDENCE so MDC.clear() is last-out");
    }

    private static void assertMdcEmpty() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        assertTrue(
                contextMap == null || contextMap.isEmpty(),
                "MDC context map should contain zero keys after the chain, but was: " + contextMap);
    }
}
