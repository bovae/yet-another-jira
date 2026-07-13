package com.bovae.yaj.auth.jwt;

import com.bovae.yaj.error.UnauthorizedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenExtractor {

    private static final String BEARER_PREFIX = "bearer ";

    public String extract(@Nullable String authorizationHeader) {
        if (StringUtils.isBlank(authorizationHeader)) {
            throw new UnauthorizedException("Missing or invalid authorization header.");
        }
        if (!authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new UnauthorizedException("Missing or invalid authorization header.");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).strip();
        if (token.isBlank()) {
            throw new UnauthorizedException("Missing or invalid authorization header.");
        }
        return token;
    }
}
