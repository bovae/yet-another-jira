package com.bovae.yaj.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void decorate_shouldPropagateSubmitterContextToRunnable_whenContextPresent() {
        MDC.put("correlationId", "abc-123");
        AtomicReference<String> seenInsideTask = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> seenInsideTask.set(MDC.get("correlationId")));
        MDC.clear(); // simulate a different (worker) thread with no context
        decorated.run();

        assertEquals("abc-123", seenInsideTask.get(), "captured MDC must be visible inside the task");
    }

    @Test
    void decorate_shouldClearContextAfterRunning_soPooledThreadDoesNotLeak() {
        MDC.put("correlationId", "abc-123");

        Runnable decorated = decorator.decorate(() -> {});
        decorated.run();

        assertNull(MDC.get("correlationId"), "MDC must be cleared after the task completes");
    }

    @Test
    void decorate_shouldClearWorkerContext_whenSubmitterHadNoContext() {
        AtomicReference<String> seenInsideTask = new AtomicReference<>("sentinel");

        Runnable decorated = decorator.decorate(() -> seenInsideTask.set(MDC.get("correlationId")));
        MDC.put("correlationId", "stale-from-pool");
        decorated.run();

        assertNull(seenInsideTask.get(), "no submitter context must clear any stale worker MDC");
    }
}
