package com.bovae.yaj.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bovae.yaj.config.properties.JwtProperties;
import com.bovae.yaj.error.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String TEST_SECRET = "this-is-a-test-secret-that-is-at-least-32-characters-long";
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final Instant FIXED_NOW = Instant.parse("2025-01-15T12:00:00Z");
    private static final SecretKey TEST_KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Mock
    private TokenDenylist tokenDenylist;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(TEST_SECRET, TOKEN_TTL, 5, Duration.ofMinutes(15));
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        jwtService = new JwtService(properties, fixedClock, tokenDenylist);
    }

    // --- issue/validate round trip ---

    @Test
    void issue_shouldProduceTokenThatValidatesAndRecoversSubIatExp() {
        UUID userId = UUID.randomUUID();
        when(tokenDenylist.contains(anyString())).thenReturn(false);

        String token = jwtService.issue(userId);
        TokenClaims claims = jwtService.validateAccessToken(token);

        assertEquals(userId, claims.subject());
        assertEquals(FIXED_NOW, claims.issuedAt());
        assertEquals(FIXED_NOW.plus(TOKEN_TTL), claims.expiresAt());
        assertNotNull(claims.jti());
    }

    @Test
    void issue_shouldProduceDistinctJtiOnEachIssuance() {
        UUID userId = UUID.randomUUID();
        when(tokenDenylist.contains(anyString())).thenReturn(false);

        String token1 = jwtService.issue(userId);
        String token2 = jwtService.issue(userId);

        TokenClaims claims1 = jwtService.validateAccessToken(token1);
        TokenClaims claims2 = jwtService.validateAccessToken(token2);

        assertNotEquals(claims1.jti(), claims2.jti());
    }

    // --- rejection cases (parametrized) ---

    @ParameterizedTest(name = "validateAccessToken rejects: {0}")
    @MethodSource("rejectedTokens")
    void validateAccessToken_shouldThrowUnauthorized_whenTokenInvalid(String caseName, String invalidToken) {
        assertThrows(UnauthorizedException.class, () -> jwtService.validateAccessToken(invalidToken));
    }

    static Stream<Arguments> rejectedTokens() {
        // Build a valid token directly with JJWT for the tamper case
        String validToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW.plus(TOKEN_TTL)))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();

        return Stream.of(
                Arguments.of("malformed", "not.a.jwt"),
                Arguments.of("signature-tampered", tamperSignature(validToken)),
                Arguments.of("expired", buildExpiredToken()),
                Arguments.of("missing-sub", buildTokenWithoutSub()),
                Arguments.of("missing-jti", buildTokenWithoutJti()),
                Arguments.of("missing-exp", buildTokenWithoutExp()),
                Arguments.of("non-uuid-sub", buildTokenWithNonUuidSub()));
    }

    // --- parse fallback / boundary branches ---

    @Test
    void validateAccessToken_shouldDefaultIssuedAtToClock_whenIatMissing() {
        when(tokenDenylist.contains(anyString())).thenReturn(false);
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .expiration(Date.from(FIXED_NOW.plus(TOKEN_TTL)))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();

        TokenClaims claims = jwtService.validateAccessToken(token);

        assertEquals(FIXED_NOW, claims.issuedAt(), "missing iat must fall back to the clock instant");
    }

    @Test
    void validateAccessToken_shouldAccept_whenExpEqualsNow() {
        when(tokenDenylist.contains(anyString())).thenReturn(false);
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();

        TokenClaims claims = jwtService.validateAccessToken(token);

        assertEquals(FIXED_NOW, claims.expiresAt(), "exp exactly at now is the boundary and must validate");
    }

    @Test
    void validateAccessToken_shouldThrowUnauthorized_whenTokenDenylisted() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issue(userId);

        when(tokenDenylist.contains(anyString())).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> jwtService.validateAccessToken(token));
    }

    // --- helpers ---

    private static String tamperSignature(String token) {
        // Flip the FIRST signature char, not the last. The last base64url char of a 32-byte HMAC
        // signature only carries 4 significant bits (its low 2 bits are dropped when decoding), so
        // flipping 'A'<->'B' there can leave the decoded signature unchanged (~1/16 of tokens) and
        // the "tampered" token would still verify. The first char's 6 bits are all significant, so
        // changing it always alters the signature and the token is guaranteed to be rejected.
        int lastDot = token.lastIndexOf('.');
        String header = token.substring(0, lastDot + 1);
        String sigPart = token.substring(lastDot + 1);
        char firstChar = sigPart.charAt(0);
        char flipped = (firstChar == 'A') ? 'B' : 'A';
        return header + flipped + sigPart.substring(1);
    }

    private static String buildExpiredToken() {
        Instant pastIat = FIXED_NOW.minus(Duration.ofHours(2));
        Instant pastExp = FIXED_NOW.minus(Duration.ofHours(1));
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(pastIat))
                .expiration(Date.from(pastExp))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();
    }

    private static String buildTokenWithoutSub() {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW.plus(TOKEN_TTL)))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();
    }

    private static String buildTokenWithoutJti() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW.plus(TOKEN_TTL)))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();
    }

    private static String buildTokenWithoutExp() {
        // Build a token payload manually without exp
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(FIXED_NOW))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();
    }

    private static String buildTokenWithNonUuidSub() {
        return Jwts.builder()
                .subject("not-a-uuid")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(FIXED_NOW))
                .expiration(Date.from(FIXED_NOW.plus(TOKEN_TTL)))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();
    }
}
