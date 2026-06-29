package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.RawTokenGenerator;
import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class VerificationTokenIssuerTest {

    private static final Instant FIXED_NOW = Instant.parse("2025-01-15T12:00:00Z");
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final String RAW_TOKEN = "dGVzdC1yYXctdG9rZW4tYWJjMTIz";
    private static final String HASHED_TOKEN = "a1b2c3d4e5f6";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String RECIPIENT_EMAIL = "user@example.com";

    @Mock
    private RawTokenGenerator rawTokenGenerator;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private VerificationProperties verificationProperties;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Captor
    private ArgumentCaptor<VerificationToken> tokenCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private VerificationTokenIssuer verificationTokenIssuer;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        verificationTokenIssuer = new VerificationTokenIssuer(
                rawTokenGenerator,
                tokenHasher,
                verificationTokenRepository,
                verificationProperties,
                fixedClock,
                applicationEventPublisher);
    }

    @Test
    void issue_shouldPersistTokenWithCorrectFields() {
        when(rawTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(verificationProperties.tokenTtl()).thenReturn(TOKEN_TTL);
        when(verificationTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        verificationTokenIssuer.issue(USER_ID, RECIPIENT_EMAIL);

        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken saved = tokenCaptor.getValue();

        assertEquals(USER_ID, saved.getUserId());
        assertEquals(VerificationPurpose.EMAIL_VERIFICATION, saved.getPurpose());
        assertEquals(FIXED_NOW.plus(TOKEN_TTL), saved.getExpiresAt(), "expiresAt must be now + 24h");
        assertNull(saved.getConsumedAt(), "consumedAt must be null on issuance");
        assertEquals(HASHED_TOKEN, saved.getTokenHash(), "persisted hash must come from TokenHasher");
        assertNotEquals(RAW_TOKEN, saved.getTokenHash(), "stored hash must differ from raw token");
    }

    @Test
    void issue_shouldPublishVerificationEmailRequestedEvent() {
        when(rawTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(verificationProperties.tokenTtl()).thenReturn(TOKEN_TTL);
        when(verificationTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        verificationTokenIssuer.issue(USER_ID, RECIPIENT_EMAIL);

        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        Object event = eventCaptor.getValue();
        assertEquals(VerificationEmailRequestedEvent.class, event.getClass());

        VerificationEmailRequestedEvent emailEvent = (VerificationEmailRequestedEvent) event;
        assertEquals(RECIPIENT_EMAIL, emailEvent.recipientEmail());
        assertEquals(RAW_TOKEN, emailEvent.rawToken(), "event must carry the raw token from RawTokenGenerator");
    }
}
