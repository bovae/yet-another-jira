package com.bovae.yaj.domain.enums;

import com.bovae.yaj.error.ValidationException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

public enum TicketType {
    BUG("bug"),
    FEATURE("feature"),
    FIX("fix");

    private static final Map<String, TicketType> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(TicketType::code, Function.identity()));

    private final String code;

    TicketType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static TicketType parse(@Nullable String code) {
        if (StringUtils.isBlank(code)) {
            throw new ValidationException("A ticket type code is required.");
        }
        TicketType type = BY_CODE.get(code);
        if (type == null) {
            throw new ValidationException("Unknown ticket type code: '" + code + "'.");
        }
        return type;
    }
}
