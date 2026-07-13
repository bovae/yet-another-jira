package com.bovae.yaj.auth.logout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.jwt.BearerTokenExtractor;
import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.auth.jwt.TokenClaims;
import com.bovae.yaj.auth.jwt.TokenDenylist;
import com.bovae.yaj.error.UnauthorizedException;
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

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2025-01-15T12:00:00Z");
    private static final String AUTH_HEADER = "Bearer some-token";
    private static final String RAW_TOKEN = "some-token";

    @Mock
    private BearerTokenExtractor bearerTokenExtractor;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenDenylist tokenDenylist;

    @Captor
    private ArgumentCaptor<String> jtiCaptor;

    @Captor
    private ArgumentCaptor<Duration> ttlCaptor;

    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        logoutService = new LogoutService(bearerTokenExtractor, jwtService, tokenDenylist, fixedClock);
    }

    // --- success: valid unexpired token records jti with correct TTL ---

    @Test
    void logout_shouldRevokeJtiWithDerivedTtl_whenTokenValidAndUnexpired() {
        Instant expiresAt = FIXED_NOW.plusSeconds(1800); // 30 min in the future
        TokenClaims claims = new TokenClaims(UUID.randomUUID(), "test-jti", FIXED_NOW, expiresAt);

        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.parseForRevocation(RAW_TOKEN)).thenReturn(claims);

        logoutService.logout(AUTH_HEADER);

        verify(tokenDenylist).revoke(jtiCaptor.capture(), ttlCaptor.capture());
        assertEquals("test-jti", jtiCaptor.getValue());
        assertEquals(Duration.ofSeconds(1800), ttlCaptor.getValue());
    }

    // --- idempotency: already-denylisted token still succeeds and re-records ---

    @Test
    void logout_shouldStillRevoke_whenTokenAlreadyDenylisted() {
        Instant expiresAt = FIXED_NOW.plusSeconds(600);
        TokenClaims claims = new TokenClaims(UUID.randomUUID(), "denylisted-jti", FIXED_NOW, expiresAt);

        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.parseForRevocation(RAW_TOKEN)).thenReturn(claims);

        logoutService.logout(AUTH_HEADER);

        verify(tokenDenylist).revoke(jtiCaptor.capture(), ttlCaptor.capture());
        assertEquals("denylisted-jti", jtiCaptor.getValue());
        assertEquals(Duration.ofSeconds(600), ttlCaptor.getValue());
    }

    // --- already-expired token: non-positive TTL is not recorded ---

    @Test
    void logout_shouldNotRevoke_whenTokenTtlNonPositive() {
        // expiresAt == now → ttl is zero, which is not positive
        TokenClaims claims = new TokenClaims(UUID.randomUUID(), "expired-jti", FIXED_NOW, FIXED_NOW);
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.parseForRevocation(RAW_TOKEN)).thenReturn(claims);

        logoutService.logout(AUTH_HEADER);

        verify(tokenDenylist, never()).revoke(any(), any());
    }

    // --- failure: missing/malformed header → UnauthorizedException ---

    @Test
    void logout_shouldThrowUnauthorized_whenHeaderMissing() {
        when(bearerTokenExtractor.extract(null))
                .thenThrow(new UnauthorizedException("Missing or invalid authorization header."));

        assertThrows(UnauthorizedException.class, () -> logoutService.logout(null));

        verifyNoInteractions(jwtService, tokenDenylist);
    }

    // --- idempotency: unparseable/expired token → no-op success (204), not 401 ---

    @Test
    void logout_shouldNoOp_whenTokenParseFails() {
        // An expired or otherwise unparseable token can't be revoked; logout stays idempotent.
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.parseForRevocation(RAW_TOKEN))
                .thenThrow(new UnauthorizedException("Invalid or expired token."));

        logoutService.logout(AUTH_HEADER);

        verifyNoInteractions(tokenDenylist);
    }
}
