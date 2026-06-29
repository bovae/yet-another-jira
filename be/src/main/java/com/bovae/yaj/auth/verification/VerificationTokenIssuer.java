package com.bovae.yaj.auth.verification;

import com.bovae.yaj.auth.token.RawTokenGenerator;
import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationTokenIssuer {

    private final RawTokenGenerator rawTokenGenerator;
    private final TokenHasher tokenHasher;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationProperties verificationProperties;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void issue(UUID userId, String recipientEmail) {
        String rawToken = rawTokenGenerator.generate();
        String hash = tokenHasher.hash(rawToken);

        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setTokenHash(hash);
        token.setPurpose(VerificationPurpose.EMAIL_VERIFICATION);
        token.setExpiresAt(clock.instant().plus(verificationProperties.tokenTtl()));
        token.setConsumedAt(null);

        verificationTokenRepository.save(token);

        applicationEventPublisher.publishEvent(new VerificationEmailRequestedEvent(recipientEmail, rawToken));
    }
}
