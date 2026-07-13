package com.bovae.yaj.auth.verification;

public record VerifyResponse(boolean verified, String message, String next) {

    public static VerifyResponse success() {
        return new VerifyResponse(true, "Your email is verified. Please log in.", "login");
    }
}
