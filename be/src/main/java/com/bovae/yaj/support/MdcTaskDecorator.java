package com.bovae.yaj.support;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Propagates the SLF4J {@link MDC} (correlation id, etc.) from the submitting thread onto the
 * executor thread so async work keeps the request's log context. Chosen over Spring's
 * {@code ContextPropagatingTaskDecorator} because Micrometer's {@code context-propagation} is not on
 * this project's classpath (and no {@code ThreadLocalAccessor} is registered for MDC without it).
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            if (submitterContext != null) {
                MDC.setContextMap(submitterContext);
            } else {
                MDC.clear();
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
