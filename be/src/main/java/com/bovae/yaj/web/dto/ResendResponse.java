package com.bovae.yaj.web.dto;

public record ResendResponse(String message) {

    public static ResendResponse uniform() {
        return new ResendResponse(
                "If an unverified account exists for that address, a new verification email has been sent.");
    }
}
