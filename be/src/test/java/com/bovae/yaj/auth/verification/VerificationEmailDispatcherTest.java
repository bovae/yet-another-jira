package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class VerificationEmailDispatcherTest {

    private static final String RECIPIENT = "user@example.com";
    private static final String RAW_TOKEN = "secret-raw-token-xyz";

    @Mock
    private VerificationEmailSender verificationEmailSender;

    @InjectMocks
    private VerificationEmailDispatcher verificationEmailDispatcher;

    @Test
    void onVerificationEmailRequested_shouldDelegateToSender() {
        var event = new VerificationEmailRequestedEvent(RECIPIENT, RAW_TOKEN);

        verificationEmailDispatcher.onVerificationEmailRequested(event);

        verify(verificationEmailSender).send(RECIPIENT, RAW_TOKEN);
    }

    @Test
    void onVerificationEmailRequested_shouldSwallowMailException() {
        var event = new VerificationEmailRequestedEvent(RECIPIENT, RAW_TOKEN);
        doThrow(new MailSendException("SMTP connection refused"))
                .when(verificationEmailSender)
                .send(RECIPIENT, RAW_TOKEN);

        assertDoesNotThrow(() -> verificationEmailDispatcher.onVerificationEmailRequested(event));
    }

    @Test
    void onVerificationEmailRequested_shouldNotLogRawToken() {
        Logger logger = (Logger) LoggerFactory.getLogger(VerificationEmailDispatcher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            var event = new VerificationEmailRequestedEvent(RECIPIENT, RAW_TOKEN);
            doThrow(new MailSendException("SMTP connection refused"))
                    .when(verificationEmailSender)
                    .send(RECIPIENT, RAW_TOKEN);

            verificationEmailDispatcher.onVerificationEmailRequested(event);

            for (ILoggingEvent logEvent : appender.list) {
                String formatted = logEvent.getFormattedMessage();
                assertTrue(!formatted.contains(RAW_TOKEN), "log line must not contain the raw token: " + formatted);
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
