package com.bovae.yaj.auth.login;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static LoginResponse bearer(String accessToken, long expiresInSeconds) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
