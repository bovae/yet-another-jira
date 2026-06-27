package com.bovae.yaj.support;

import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.lang.Nullable;

@UtilityClass
public class UUIDUtils {

    public String getRandomUUID() {
        return UUID.randomUUID().toString();
    }

    public boolean isValidUUID(@Nullable String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
