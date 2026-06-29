package com.bovae.yaj.auth.verification;

import com.bovae.yaj.config.properties.SmtpProperties;
import com.bovae.yaj.config.properties.VerificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class VerificationEmailSender {

    private final JavaMailSender javaMailSender;
    private final VerificationProperties verificationProperties;
    private final SmtpProperties smtpProperties;

    public void send(String recipientEmail, String rawToken) {
        String link = UriComponentsBuilder.fromUriString(verificationProperties.linkBaseUrl())
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtpProperties.from());
        message.setTo(recipientEmail);
        message.setSubject("Verify your email address");
        message.setText("Please verify your email by clicking the link below:\n\n" + link);

        javaMailSender.send(message);
    }
}
