package com.bovae.yaj.auth.jwt;

import com.bovae.yaj.config.properties.JwtProperties;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.support.UUIDUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String INVALID_TOKEN_MSG = "Invalid or expired token.";

    private final SecretKey key;
    private final Clock clock;
    private final TokenDenylist tokenDenylist;
    private final Duration tokenTtl;

    public JwtService(JwtProperties jwtProperties, Clock clock, TokenDenylist tokenDenylist) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
        this.tokenDenylist = tokenDenylist;
        this.tokenTtl = jwtProperties.tokenTtl();
    }

    public String issue(UUID userId) {
        Instant now = clock.instant();
        Instant exp = now.plus(tokenTtl);
        String jti = UUIDUtils.getRandomUUID();

        return Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public TokenClaims validateAccessToken(String token) {
        TokenClaims claims = parse(token);
        if (tokenDenylist.contains(claims.jti())) {
            throw new UnauthorizedException(INVALID_TOKEN_MSG);
        }
        return claims;
    }

    public TokenClaims parseForRevocation(String token) {
        return parse(token);
    }

    private TokenClaims parse(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            String sub = claims.getSubject();
            String jti = claims.getId();
            Date exp = claims.getExpiration();

            if (sub == null || jti == null || exp == null) {
                throw new UnauthorizedException(INVALID_TOKEN_MSG);
            }

            UUID subject = UUID.fromString(sub);
            Instant issuedAt =
                    claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : clock.instant();
            Instant expiresAt = exp.toInstant();

            return new TokenClaims(subject, jti, issuedAt, expiresAt);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(INVALID_TOKEN_MSG);
        }
    }
}
