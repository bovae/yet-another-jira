package com.bovae.yaj.domain.enums;

import com.bovae.yaj.error.ValidationException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

@RequiredArgsConstructor
public enum TicketState {
    NEW("new"),
    READY_FOR_IMPLEMENTATION("ready_for_implementation"),
    IN_PROGRESS("in_progress"),
    READY_FOR_ACCEPTANCE("ready_for_acceptance"),
    DONE("done");

    private static final Map<String, TicketState> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(TicketState::code, Function.identity()));

    private final String code;

    public String code() {
        return code;
    }

    public static TicketState parse(@Nullable String code) {
        if (StringUtils.isBlank(code)) {
            throw new ValidationException("A ticket state code is required.");
        }
        TicketState state = BY_CODE.get(code);
        if (state == null) {
            throw new ValidationException("Unknown ticket state code: '" + code + "'.");
        }
        return state;
    }
}
