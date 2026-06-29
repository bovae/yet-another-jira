package com.bovae.yaj.auth.logout;

import com.bovae.yaj.auth.jwt.BearerTokenExtractor;
import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.auth.jwt.TokenClaims;
import com.bovae.yaj.auth.jwt.TokenDenylist;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final BearerTokenExtractor bearerTokenExtractor;
    private final JwtService jwtService;
    private final TokenDenylist tokenDenylist;
    private final Clock clock;

    public void logout(@Nullable String authorizationHeader) {
        String token = bearerTokenExtractor.extract(authorizationHeader);
        TokenClaims claims = jwtService.parseForRevocation(token);
        Duration ttl = Duration.between(clock.instant(), claims.expiresAt());
        if (ttl.isPositive()) {
            tokenDenylist.revoke(claims.jti(), ttl);
        }
    }
}
