package com.bovae.yaj.auth.verification;

import static com.bovae.yaj.config.AsyncConfig.VERIFICATION_EMAIL_EXECUTOR;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
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
        } catch (MailException ex) {
            LOG.warn("Verification email dispatch failed for a recipient; user can recover via resend", ex);
        }
    }
}
