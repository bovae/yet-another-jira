package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.config.properties.SmtpProperties;
import com.bovae.yaj.config.properties.VerificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class VerificationEmailSenderTest {

    private static final String LINK_BASE_URL = "https://example.com/verify";
    private static final String FROM_ADDRESS = "noreply@example.com";
    private static final String RECIPIENT = "user@example.com";
    private static final String RAW_TOKEN = "raw-token-123";

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private VerificationProperties verificationProperties;

    @Mock
    private SmtpProperties smtpProperties;

    @InjectMocks
    private VerificationEmailSender verificationEmailSender;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    @Test
    void send_shouldBuildLinkAndSendEmail() {
        when(verificationProperties.linkBaseUrl()).thenReturn(LINK_BASE_URL);
        when(smtpProperties.from()).thenReturn(FROM_ADDRESS);

        verificationEmailSender.send(RECIPIENT, RAW_TOKEN);

        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage sent = messageCaptor.getValue();

        assertEquals(FROM_ADDRESS, sent.getFrom(), "from must match smtpProperties.from()");
        String[] to = sent.getTo();
        assertEquals(1, to.length);
        assertEquals(RECIPIENT, to[0], "to must be the recipient email");
        assertTrue(
                sent.getText().contains("https://example.com/verify?token=raw-token-123"),
                "body must contain the verification link with the raw token");
    }
}
