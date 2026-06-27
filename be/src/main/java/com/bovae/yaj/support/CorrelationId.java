package com.bovae.yaj.support;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    public static final String MDC_KEY = "correlationId";
}
