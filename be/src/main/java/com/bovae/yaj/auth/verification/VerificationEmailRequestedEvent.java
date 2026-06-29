package com.bovae.yaj.auth.verification;

public record VerificationEmailRequestedEvent(String recipientEmail, String rawToken) {}
