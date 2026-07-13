package com.bovae.yaj.auth.verification;

import static com.bovae.yaj.config.AsyncConfig.VERIFICATION_EMAIL_EXECUTOR;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationEmailDispatcher {

    private final VerificationEmailSender verificationEmailSender;

    @Async(VERIFICATION_EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequestedEvent event) {
        try {
            verificationEmailSender.send(event.recipientEmail(), event.rawToken());
        } catch (RuntimeException ex) {
            // Runs after commit on the async executor, so an exception here has nowhere to propagate.
            // Catch broadly (a non-MailException — DNS, socket, config — must not crash the executor)
            // and log the type + message only: never the recipient, since this fires per user.
            LOG.warn(
                    "Verification email dispatch failed ({}): {}; user can recover via resend",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }
}
